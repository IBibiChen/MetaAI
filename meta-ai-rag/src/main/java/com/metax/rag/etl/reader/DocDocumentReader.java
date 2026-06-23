package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * DocDocumentReader .
 *
 * <p>
 * 旧版 Word .doc 文档 Reader，先通过 LibreOffice headless 转换为临时 docx，再复用 Tika 读取 docx 文本
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
@Slf4j
public class DocDocumentReader implements DocumentReader {

    private static final Duration CONVERSION_TIMEOUT = Duration.ofMinutes(3);

    private static final int MAX_LOG_OUTPUT_CHARS = 1000;

    private final MetaDocumentResource documentResource;

    /**
     * 创建旧版 Word 文档 Reader
     *
     * @param documentResource 文档资源
     */
    public DocDocumentReader(MetaDocumentResource documentResource) {
        this.documentResource = documentResource;
    }

    /**
     * 读取旧版 Word 文档
     *
     * <p>
     * .doc 旧二进制格式直接交给 POI / Tika 容易在异常结构上失败
     * 这里用 LibreOffice 完成格式兼容，再让现有 Tika 链路处理标准 docx
     * /tmp/meta-ai-doc-reader-1867293056123456789/input.doc
     * /tmp/meta-ai-doc-reader-1867293056123456789/input.docx
     * /tmp/meta-ai-doc-reader-1867293056123456789/soffice-output.log
     *
     * @return Document 列表
     */
    @Override
    public List<Document> get() {
        Path workDir = createWorkDir();
        try {
            // 本次转换的输入、输出和进程日志都放在同一个随机 workDir 下，finally 会统一删除整个目录
            Path input = workDir.resolve("input.doc");
            Path output = workDir.resolve("input.docx");
            Path processOutput = workDir.resolve("soffice-output.log");
            copySource(input);
            Path converted = convertToDocx(workDir, input, output, processOutput);
            List<Document> documents = new TikaDocumentReader(new FileSystemResource(converted)).read();
            log.info("Doc 文档读取完成：source = {}，documents = {}",
                    documentResource.source(), documents.size());
            return documents;
        } finally {
            cleanup(workDir);
        }
    }

    /**
     * 创建本次转换使用的临时目录
     *
     * <p>
     * meta-ai-doc-reader- 后面的随机后缀由 JDK 生成，只用于保证系统临时目录下不重名
     * 该后缀不是业务 ID，也不能被后续逻辑依赖
     *
     * <p>
     * Linux 容器示例：/tmp/meta-ai-doc-reader-1867293056123456789
     *
     * @return 临时目录
     */
    private Path createWorkDir() {
        try {
            return Files.createTempDirectory("meta-ai-doc-reader-");
        } catch (IOException ex) {
            throw new IllegalStateException("Doc 文档临时目录创建失败：" + documentResource.source(), ex);
        }
    }

    /**
     * 复制对象存储原始 .doc 到临时目录
     *
     * @param input 临时输入文件
     */
    private void copySource(Path input) {
        try (InputStream stream = documentResource.resource().getInputStream()) {
            Files.copy(stream, input);
        } catch (IOException ex) {
            throw new IllegalStateException("Doc 文档临时文件写入失败：" + documentResource.source(), ex);
        }
    }

    /**
     * 调用 LibreOffice headless 把 .doc 转换为 .docx
     *
     * @param workDir       本次转换临时目录
     * @param input         临时输入文件
     * @param output        预期输出文件
     * @param processOutput LibreOffice 标准输出和错误输出日志
     * @return 实际转换产物
     */
    private Path convertToDocx(Path workDir, Path input, Path output, Path processOutput) {
        List<String> command = command(workDir, input);
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(processOutput.toFile())
                    .start();
        } catch (IOException ex) {
            throw new IllegalStateException("Doc 文档转换命令启动失败：" + documentResource.source(), ex);
        }
        waitForConversion(process, processOutput);
        return resolveConverted(workDir, output, processOutput);
    }

    /**
     * 组装 LibreOffice 转换命令
     *
     * <p>
     * LibreOffice convert-to 只能指定目标格式和输出目录，输出文件名由输入文件 basename 决定
     * 当前临时输入固定为 input.doc，因此转换产物预期为 input.docx
     *
     * <p>
     * 示例命令
     * -
     * soffice \
     * -env:UserInstallation=file:///tmp/meta-ai-doc-reader-1867293056123456789/lo-profile/ \
     * --headless \
     * --nologo \
     * --nofirststartwizard \
     * --norestore \
     * --convert-to docx \
     * --outdir /tmp/meta-ai-doc-reader-1867293056123456789 \
     * /tmp/meta-ai-doc-reader-1867293056123456789/input.doc
     *
     * @param workDir 本次转换临时目录
     * @param input   临时输入文件
     * @return 转换命令
     */
    private List<String> command(Path workDir, Path input) {
        List<String> command = new ArrayList<>();
        // LibreOffice 命令行入口，Docker 镜像中由 libreoffice-writer-nogui 包提供
        command.add("soffice");
        // UserInstallation 指向本次随机 workDir 下的独立 profile，避免并发 headless 进程争抢默认 profile 锁
        // lo-profile 只是固定子目录名，用于隔离 LibreOffice 运行文件和 input.doc / input.docx / soffice-output.log
        // 真正保证 profile 不共享的是完整随机路径，例如 /tmp/meta-ai-doc-reader-111/lo-profile 和 /tmp/meta-ai-doc-reader-222/lo-profile
        command.add("-env:UserInstallation=" + workDir.resolve("lo-profile").toUri());
        // 使用无界面模式，适合服务端容器内批量转换
        command.add("--headless");
        // 禁用启动 Logo，减少无意义启动动作
        command.add("--nologo");
        // 跳过首次启动向导，避免新 profile 初始化时出现交互流程
        command.add("--nofirststartwizard");
        // 禁用崩溃恢复流程，避免异常退出后的恢复窗口影响 headless 转换
        command.add("--norestore");
        // 声明执行文件格式转换
        command.add("--convert-to");
        // 目标格式固定为 docx，转换后的 input.docx 只作为临时中间产物给 Tika 读取
        command.add("docx");
        // 指定转换产物输出目录，避免 LibreOffice 默认写入当前工作目录
        command.add("--outdir");
        // 输出目录使用本次转换临时目录，便于转换结束后统一清理
        command.add(workDir.toString());
        // 待转换的 .doc 临时输入文件，LibreOffice 会按 basename 在 outdir 下生成 input.docx
        command.add(input.toString());
        return command;
    }

    /**
     * 等待 LibreOffice 转换结束
     *
     * @param process       LibreOffice 进程
     * @param processOutput LibreOffice 标准输出和错误输出日志
     */
    private void waitForConversion(Process process, Path processOutput) {
        try {
            boolean finished = process.waitFor(CONVERSION_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Doc 文档转换超时：source = {}，timeout = {}，output = {}",
                        documentResource.source(), CONVERSION_TIMEOUT, readConversionOutput(processOutput));
                throw new IllegalStateException("Doc 文档转换超时：" + documentResource.source()
                        + "，timeout = " + CONVERSION_TIMEOUT);
            }
            if (process.exitValue() != 0) {
                log.warn("Doc 文档转换失败：source = {}，exitCode = {}，output = {}",
                        documentResource.source(), process.exitValue(), readConversionOutput(processOutput));
                throw new IllegalStateException("Doc 文档转换失败：" + documentResource.source()
                        + "，exitCode = " + process.exitValue());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Doc 文档转换被中断：" + documentResource.source(), ex);
        }
    }

    /**
     * 读取 LibreOffice 转换输出
     *
     * <p>
     * 输出只进入应用日志，不进入异常 message，避免 last_error 被外部进程输出撑爆
     * 日志侧仍限制长度，防止 LibreOffice 输出异常膨胀
     *
     * @param processOutput LibreOffice 标准输出和错误输出日志
     * @return 失败诊断输出
     */
    private String readConversionOutput(Path processOutput) {
        try {
            if (!Files.isRegularFile(processOutput)) {
                return "";
            }
            String output = Files.readString(processOutput, StandardCharsets.UTF_8).trim();
            if (output.length() <= MAX_LOG_OUTPUT_CHARS) {
                return output;
            }
            return output.substring(0, MAX_LOG_OUTPUT_CHARS) + "...";
        } catch (IOException ex) {
            return "读取转换输出失败：" + ex.getMessage();
        }
    }

    /**
     * 解析 LibreOffice 转换产物
     *
     * <p>
     * 正常情况下 LibreOffice 会按输入 basename 生成 input.docx
     * 这里只接受该预期产物，避免在异常临时目录里误读其他 docx 文件
     *
     * @param workDir       本次转换临时目录
     * @param output        预期输出文件
     * @param processOutput LibreOffice 标准输出和错误输出日志
     * @return 实际转换产物
     */
    private Path resolveConverted(Path workDir, Path output, Path processOutput) {
        try {
            if (Files.isRegularFile(output) && Files.size(output) > 0) {
                return output;
            }
            log.warn("Doc 文档转换产物不存在或为空：source = {}，workDir = {}，expected = {}，output = {}，files = {}",
                    documentResource.source(), workDir, output.getFileName(), readConversionOutput(processOutput),
                    listWorkDir(workDir));
            throw new IllegalStateException("Doc 文档转换产物不存在或为空：" + documentResource.source());
        } catch (IOException ex) {
            throw new IllegalStateException("Doc 文档转换产物校验失败：" + documentResource.source(), ex);
        }
    }

    /**
     * 列出临时目录内容
     *
     * <p>
     * 仅记录文件名和大小，便于诊断 LibreOffice 是否生成了非预期文件
     *
     * @param workDir 本次转换临时目录
     * @return 目录内容摘要
     */
    private String listWorkDir(Path workDir) {
        try (Stream<Path> paths = Files.list(workDir)) {
            return paths
                    .map(this::fileSummary)
                    .toList()
                    .toString();
        } catch (IOException ex) {
            return "读取临时目录失败：" + ex.getMessage();
        }
    }

    /**
     * 构造临时目录单个文件摘要
     *
     * @param path 文件路径
     * @return 文件名和大小摘要
     */
    private String fileSummary(Path path) {
        try {
            String type = Files.isDirectory(path) ? "dir" : "file";
            long size = Files.isRegularFile(path) ? Files.size(path) : 0;
            return "%s(%s,%d)".formatted(path.getFileName(), type, size);
        } catch (IOException ex) {
            return "%s(error)".formatted(path.getFileName());
        }
    }

    /**
     * 清理本次转换临时目录
     *
     * @param workDir 本次转换临时目录
     */
    private void cleanup(Path workDir) {
        try {
            FileSystemUtils.deleteRecursively(workDir);
        } catch (IOException ex) {
            log.warn("Doc 文档临时目录清理失败：source = {}，workDir = {}",
                    documentResource.source(), workDir, ex);
        }
    }
}
