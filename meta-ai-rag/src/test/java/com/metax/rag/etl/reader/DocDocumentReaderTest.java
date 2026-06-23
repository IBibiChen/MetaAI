package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocDocumentReaderTest .
 *
 * <p>
 * 旧版 Word .doc 文档 Reader 测试
 */
class DocDocumentReaderTest {

    /**
     * 原始 Resource 读取失败时应给出清晰错误
     */
    @Test
    void shouldFailWhenSourceCannotBeRead() {
        DocDocumentReader reader = new DocDocumentReader(brokenResource());

        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Doc 文档临时文件写入失败");
    }

    /**
     * 转换命令应使用每次转换独占的 LibreOffice user profile
     */
    @Test
    void commandShouldUseIsolatedLibreOfficeProfile() throws Exception {
        DocDocumentReader reader = new DocDocumentReader(brokenResource());
        Path workDir = Path.of("target", "meta-ai-doc-reader-test");
        Path input = workDir.resolve("input.doc");

        List<String> command = command(reader, workDir, input);

        assertThat(command)
                .contains("soffice")
                .contains("--headless")
                .contains("--convert-to")
                .contains("docx")
                .contains("--outdir")
                .contains(workDir.toString())
                .contains(input.toString());
        assertThat(command)
                .anySatisfy(item -> assertThat(item)
                        .startsWith("-env:UserInstallation=file:")
                        .contains("lo-profile"));
    }

    /**
     * LibreOffice 输出不应进入异常 message，避免外部同步 last_error 被长日志撑爆
     */
    @Test
    void conversionFailureShouldKeepExceptionMessageShort() throws Exception {
        DocDocumentReader reader = new DocDocumentReader(brokenResource());
        Path processOutput = Files.createTempFile("soffice-output", ".log");
        Files.writeString(processOutput, "LibreOffice diagnostic output", StandardCharsets.UTF_8);

        try {
            assertThatThrownBy(() -> waitForConversion(reader, new FinishedProcess(1), processOutput))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Doc 文档转换失败")
                    .hasMessageContaining("exitCode = 1")
                    .satisfies(ex -> assertThat(ex.getMessage())
                            .doesNotContain("output =")
                            .doesNotContain("LibreOffice diagnostic output"));
        } finally {
            Files.deleteIfExists(processOutput);
        }
    }

    /**
     * 通过反射读取私有转换命令
     *
     * <p>
     * command 是生产内部细节，不为测试放宽可见性
     *
     * @param reader  Doc Reader
     * @param workDir 本次转换临时目录
     * @param input   临时输入文件
     * @return 转换命令
     */
    @SuppressWarnings("unchecked")
    private List<String> command(DocDocumentReader reader, Path workDir, Path input) throws Exception {
        Method method = DocDocumentReader.class.getDeclaredMethod("command", Path.class, Path.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(reader, workDir, input);
    }

    /**
     * 通过反射执行私有转换等待逻辑
     *
     * <p>
     * 这里验证异常边界，不为了测试把生产私有方法放宽可见性
     *
     * @param reader        Doc Reader
     * @param process       LibreOffice 进程
     * @param processOutput LibreOffice 输出日志
     */
    private void waitForConversion(DocDocumentReader reader, Process process, Path processOutput) throws Exception {
        Method method = DocDocumentReader.class.getDeclaredMethod("waitForConversion", Process.class, Path.class);
        method.setAccessible(true);
        try {
            method.invoke(reader, process, processOutput);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            if (ex.getCause() instanceof Error cause) {
                throw cause;
            }
            throw ex;
        }
    }

    /**
     * 创建读取必定失败的测试资源
     *
     * @return 文档资源
     */
    private MetaDocumentResource brokenResource() {
        ByteArrayResource resource = new ByteArrayResource("doc-bytes".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public java.io.InputStream getInputStream() throws IOException {
                throw new IOException("broken stream");
            }

            @Override
            public String getFilename() {
                return "broken.doc";
            }
        };
        return new MetaDocumentResource(resource, "doc", "broken.doc");
    }

    /**
     * 已结束的测试进程
     *
     * <p>
     * 只模拟 waitForConversion 依赖的最小 Process 行为
     */
    private static class FinishedProcess extends Process {

        private final int exitCode;

        private FinishedProcess(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }
    }
}
