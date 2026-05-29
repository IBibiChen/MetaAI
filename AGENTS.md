# AGENTS.md instructions for D:\ideaResource\MetaAI

## 代码注释规则补充

### 既有注释语义保护

- 不得为了“规范化”“简洁化”“生产级表达”无故删除、重写或压缩既有注释语义
- 临时笔记、排查记录、命令示例、现象示例、历史说明只要仍有上下文价值，就必须保留
- 如需规范化这类注释，只能修正标点、空格、行尾规则等表层格式，不得改变信息量
- 确实要删除临时笔记时，必须有明确指令或能证明该信息已经失效

### 类级 JavaDoc 首行摘要

- 类级 JavaDoc 首行摘要允许并推荐使用 `XxxConfig .` 形式
- 类名与英文句号之间必须保留一个空格
- 该规则仅适用于类级 JavaDoc 首行摘要，不适用于方法、字段、`@Bean` 方法、`@param` 或 `@return`
- 示例：
    - `ChatClientConfig .`
    - `VectorStoreConfig .`
    - `RedisConfig .`

### 方法级 JavaDoc 首行摘要

- 方法级 JavaDoc 首行摘要仍按短语处理，默认不加句号
- `@Bean` 方法 JavaDoc 必须说明用途和关键绑定关系，但不得无故重写既有注释语义

### 注释单行行尾标点

- JavaDoc、`<p>` 正文、普通块注释、行内短注释的每一行结尾不得使用标点符号
- 行中可以根据语义使用中文逗号、中文冒号、中文句号、括号、斜杠等必要标点
- 禁止写法：`用于核实多 ChatModel / EmbeddingModel / VectorStore 的实际装配情况 (bean 名与具体类型)，`
- 推荐写法：`用于核实多 ChatModel / EmbeddingModel / VectorStore 的实际装配情况 (bean 名与具体类型)`
- 类级 JavaDoc 首行摘要的 `XxxConfig .` 是唯一明确例外
