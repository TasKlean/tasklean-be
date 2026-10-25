package com.tasklean.api.common.email.html;

import org.junit.jupiter.api.Test;

import static com.tasklean.api.common.email.html.EmailBlock.button;
import static com.tasklean.api.common.email.html.EmailBlock.code;
import static com.tasklean.api.common.email.html.EmailBlock.dataRow;
import static com.tasklean.api.common.email.html.EmailBlock.heading;
import static com.tasklean.api.common.email.html.EmailBlock.note;
import static com.tasklean.api.common.email.html.EmailBlock.paragraph;
import static org.assertj.core.api.Assertions.assertThat;

class EmailBlockTest {

    // --- escaping ---

    @Test
    void toHtml_valueContainsMarkup_isEscaped() {
        // A device name comes from the client's User-Agent, so it reaches a template unvetted.
        EmailBlock block = dataRow("Device", "<script>alert(1)</script>");

        assertThat(block.toHtml())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void toText_valueContainsMarkup_isLeftAlone() {
        // Escaping in the text part would show the reader the entities themselves.
        assertThat(dataRow("Device", "Chrome & Edge").toText()).isEqualTo("Device: Chrome & Edge");
    }

    @Test
    void toHtml_urlWithQueryParameters_escapesTheAmpersand() {
        String html = button("Verify email", "https://app.test/verify?email=a%40b.com&code=123456").toHtml();

        assertThat(html).contains("&amp;code=123456").doesNotContain("\"&code");
    }

    // --- button ---

    @Test
    void button_rendersThePillAndTheFullUrl() {
        // The URL is written out as well as linked, so a client that strips the button, or a
        // reader who cannot tap it, still has the link.
        String html = button("Reset password", "https://app.test/reset-password?token=abc").toHtml();

        assertThat(html)
                .contains(">Reset password</a>")
                .contains("If the button doesn't work")
                .contains(">https://app.test/reset-password?token=abc</a>");
    }

    @Test
    void button_toText_carriesTheLabelAndUrl() {
        assertThat(button("Reset password", "https://app.test/r").toText())
                .isEqualTo("Reset password:\nhttps://app.test/r");
    }

    // --- the remaining blocks ---

    @Test
    void textBlocks_renderTheirCopyInBothParts() {
        assertThat(heading("Verify your email").toHtml()).contains(">Verify your email</h1>");
        assertThat(heading("Verify your email").toText()).isEqualTo("Verify your email");
        assertThat(paragraph("Some copy.").toHtml()).contains(">Some copy.</p>");
        assertThat(paragraph("Some copy.").toText()).isEqualTo("Some copy.");
        assertThat(note("Expires in 5 minutes.").toHtml()).contains(">Expires in 5 minutes.</p>");
        assertThat(note("Expires in 5 minutes.").toText()).isEqualTo("Expires in 5 minutes.");
    }

    @Test
    void code_rendersTheCodeInBothParts() {
        assertThat(code("123456").toHtml()).contains(">123456</td>");
        assertThat(code("123456").toText()).isEqualTo("123456");
    }
}
