package com.tasklean.api.common.email.html;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tasklean.api.common.email.html.EmailBlock.heading;
import static com.tasklean.api.common.email.html.EmailBlock.note;
import static com.tasklean.api.common.email.html.EmailBlock.paragraph;
import static org.assertj.core.api.Assertions.assertThat;

class EmailLayoutTest {

    private static final List<EmailBlock> BLOCKS =
            List.of(heading("A title"), paragraph("Some copy."), note("A note."));

    @Test
    void renderHtml_wrapsTheBlocksInTheShell() {
        String html = EmailLayout.renderHtml(BLOCKS);

        assertThat(html)
                .startsWith("<!DOCTYPE html>")
                .endsWith("</html>\n")
                .contains(">A title</h1>", ">Some copy.</p>", ">A note.</p>");
    }

    @Test
    void renderHtml_declaresBothSchemesAndRestylesForDark() {
        String html = EmailLayout.renderHtml(BLOCKS);

        // The meta tells a client the message is drawn for both schemes; the query is what the
        // clients that honour it actually act on.
        assertThat(html)
                .contains("<meta name=\"color-scheme\" content=\"light dark\">")
                .contains("@media (prefers-color-scheme: dark)")
                // Inline styles win unless the override is flagged important.
                .contains("!important");
    }

    @Test
    void renderHtml_referencesTheInlineLogo() {
        assertThat(EmailLayout.renderHtml(BLOCKS)).contains("src=\"cid:" + EmailLayout.LOGO_CID + "\"");
    }

    @Test
    void renderText_joinsTheBlocksWithoutMarkup() {
        String text = EmailLayout.renderText(BLOCKS);

        assertThat(text).isEqualTo("A title\n\nSome copy.\n\nA note.");
        assertThat(text).doesNotContain("<");
    }
}
