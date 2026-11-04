package com.yuelei.appforge.ai;

import com.yuelei.appforge.ai.model.HtmlCodeResult;
import com.yuelei.appforge.ai.model.MultiFileCodeResult;
import com.yuelei.appforge.core.CodeParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CodeParserTest {

    @Test
    void parseHtmlCode() {
        String codeContent = """
                随便写一段描述：
                html 格式
                <!DOCTYPE html>
                <html>
                <head>
                    <title>测试页面</title>
                </head>
                <body>
                    <h1>Hello World!</h1>
                </body>
                </html>

                随便写一段描述
                """;
        HtmlCodeResult result = CodeParser.parseHtmlCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
    }

    @Test
    void parseMultiFileCode() {
        String codeContent = """
            创建一个完整的网页：

            ```html
            <!DOCTYPE html>
            <html>
            <head>
                <title>多文件示例</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <h1>欢迎使用</h1>
                <script src="script.js"></script>
            </body>
            </html>
            ```

            ```css
            h1 {
                color: blue;
                text-align: center;
            }
            ```

            ```javascript
            console.log('页面加载完成');
            ```

            文件创建完成！
            """;

        MultiFileCodeResult result =
            CodeParser.parseMultiFileCode(codeContent);

        assertNotNull(result, "解析结果不能为 null");
        assertNotNull(result.getHtmlCode(), "未解析到 HTML 代码");
        assertNotNull(result.getCssCode(), "未解析到 CSS 代码");
        assertNotNull(result.getJsCode(), "未解析到 JavaScript 代码");
    }
}
