// 来源：公众号@小林coding
// 后端八股网站：xiaolincoding.com
// Agent网站：xiaolinnote.com
// 简历模版：jianli.xiaolinnote.com


package com.mewcode.toolresult;

import com.mewcode.conversation.ConversationManager;
import com.mewcode.conversation.Message;
import com.mewcode.conversation.ToolResultBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultBudgetTest {

    private static String repeat(String c, int n) {
        return c.repeat(n);
    }

    private static ConversationManager oneToolResultMsg(ToolResultBlock... results) {
        var conv = new ConversationManager();
        conv.addToolResultsMessage(List.of(results));
        return conv;
    }

    @Test
    void applyMutatesConvInPlace(@TempDir Path dir) {
        String big = repeat("x", ToolResultBudget.SINGLE_RESULT_LIMIT + 100);
        var conv = oneToolResultMsg(new ToolResultBlock("t1", big, false));
        var state = new ContentReplacementState();

        List<ContentReplacementRecord> records = ToolResultBudget.apply(conv, dir, state);

        String content = conv.getMessages().get(0).getToolResults().get(0).content();
        assertTrue(content.startsWith("<persisted-output>"), "conv must be mutated in place with spill preview");
        assertFalse(records.isEmpty());
    }

    @Test
    void firstCallFreezesUnreplaced(@TempDir Path dir) {
        String small = repeat("y", 100);
        var conv = oneToolResultMsg(new ToolResultBlock("t1", small, false));
        var state = new ContentReplacementState();

        List<ContentReplacementRecord> records = ToolResultBudget.apply(conv, dir, state);
        assertTrue(state.seenIds().contains("t1"));
        assertFalse(state.replacements().containsKey("t1"));
        assertTrue(records.isEmpty());
    }

    @Test
    void replacementByteIdentical(@TempDir Path dir) {
        String big = repeat("z", ToolResultBudget.SINGLE_RESULT_LIMIT + 200);
        var state = new ContentReplacementState();

        var conv1 = oneToolResultMsg(new ToolResultBlock("t_big", big, false));
        List<ContentReplacementRecord> r1 = ToolResultBudget.apply(conv1, dir, state);
        String c1 = conv1.getMessages().get(0).getToolResults().get(0).content();

        var conv2 = oneToolResultMsg(new ToolResultBlock("t_big", big, false));
        List<ContentReplacementRecord> r2 = ToolResultBudget.apply(conv2, dir, state);
        String c2 = conv2.getMessages().get(0).getToolResults().get(0).content();

        assertEquals(c1, c2, "second pass must produce byte-identical content");
        assertEquals(1, r1.size());
        assertTrue(r2.isEmpty(), "re-apply must not produce new records");
        assertEquals(c1, state.replacements().get("t_big"));
    }

    @Test
    void frozenNeverReplaced(@TempDir Path dir) {
        int quarter = ToolResultBudget.MESSAGE_AGGREGATE_LIMIT / 4;
        var first = new ToolResultBlock("t1", repeat("a", quarter), false);
        var conv = oneToolResultMsg(first);
        var state = new ContentReplacementState();
        ToolResultBudget.apply(conv, dir, state);
        assertFalse(state.replacements().containsKey("t1"));

        var huge = new ToolResultBlock("t2",
                repeat("b", ToolResultBudget.SINGLE_RESULT_LIMIT + 200), false);
        var conv2 = oneToolResultMsg(first, huge);

        ToolResultBudget.apply(conv2, dir, state);
        String t1Content = conv2.getMessages().get(0).getToolResults().stream()
                .filter(tr -> tr.toolUseId().equals("t1"))
                .findFirst().orElseThrow().content();
        assertEquals(first.content(), t1Content, "t1 must stay raw — its decision was frozen");
        assertFalse(state.replacements().containsKey("t1"),
                "t1 must never enter Replacements after being frozen");
    }

    @Test
    void aggregateOnlyPicksFresh(@TempDir Path dir) {
        int bigUnder = ToolResultBudget.SINGLE_RESULT_LIMIT - 1;
        var results = new ArrayList<ToolResultBlock>();
        for (String id : new String[]{"t1", "t2", "t3", "t4", "t5"}) {
            results.add(new ToolResultBlock(id, repeat("a", bigUnder), false));
        }
        var conv = new ConversationManager();
        conv.addToolResultsMessage(results);
        var state = new ContentReplacementState();

        ToolResultBudget.apply(conv, dir, state);
        int total = 0;
        for (var tr : conv.getMessages().get(0).getToolResults()) {
            total += tr.content().length();
        }
        assertTrue(total <= ToolResultBudget.MESSAGE_AGGREGATE_LIMIT,
                "conv aggregate %d exceeds limit %d".formatted(
                        total, ToolResultBudget.MESSAGE_AGGREGATE_LIMIT));
        for (String id : new String[]{"t1", "t2", "t3", "t4", "t5"}) {
            assertTrue(state.seenIds().contains(id), id + " missing from SeenIDs");
        }
    }

    @Test
    void reconstructFromRecords() {
        var msg = new Message("user", "");
        msg.setToolResults(List.of(
                new ToolResultBlock("t1", "raw", false),
                new ToolResultBlock("t2", "raw", false)
        ));
        var records = List.of(
                ContentReplacementRecord.toolResult("t1", "t1_preview")
        );
        var state = ContentReplacementLifecycle.reconstruct(List.of(msg), records, null);
        assertTrue(state.seenIds().contains("t1"));
        assertTrue(state.seenIds().contains("t2"));
        assertEquals("t1_preview", state.replacements().get("t1"));
        assertFalse(state.replacements().containsKey("t2"));
    }

    @Test
    void reconstructWithInheritedParent() {
        var msg = new Message("user", "");
        msg.setToolResults(List.of(
                new ToolResultBlock("t_parent", "raw", false),
                new ToolResultBlock("t_child", "raw", false)
        ));
        var records = List.of(
                ContentReplacementRecord.toolResult("t_child", "child_preview")
        );
        var inherited = Map.of("t_parent", "parent_preview");
        var state = ContentReplacementLifecycle.reconstruct(List.of(msg), records, inherited);
        assertEquals("child_preview", state.replacements().get("t_child"));
        assertEquals("parent_preview", state.replacements().get("t_parent"));
    }
}
