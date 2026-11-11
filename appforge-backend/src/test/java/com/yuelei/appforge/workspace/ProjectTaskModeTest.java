package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.workspace.vue.ProjectTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 创建模式只能新增文件、修改模式必须先读后改，文件事件携带真实操作。 */
class ProjectTaskModeTest {

    @Test
    void createModeOnlyAllowsNewFilesAndEmitsCreateEvents() {
        List<FileChangeEvent> events = new ArrayList<>();
        try (ProjectTask task =
                new ProjectTask(
                        Map.of("src/App.vue", "<template>old</template>"),
                        progress -> {},
                        events::add,
                        GenerationMode.CREATE)) {
            assertThrows(
                    BusinessException.class,
                    () -> task.write("src/App.vue", "<template>overwrite</template>"));
            assertEquals("文件已保存", task.write("src/New.vue", "<template>new</template>"));
        }
        assertEquals(1, events.size());
        assertEquals("src/New.vue", events.get(0).path());
        assertEquals(FileOperation.CREATE, events.get(0).operation());
        assertTrue(events.get(0).content().contains("new"));
        assertFalse(events.get(0).truncated());
    }

    @Test
    void editModeRequiresReadBeforeModifyAndEmitsUpdateEvent() {
        List<FileChangeEvent> events = new ArrayList<>();
        try (ProjectTask task =
                new ProjectTask(
                        Map.of("src/Old.vue", "<template>old</template>"),
                        progress -> {},
                        events::add,
                        GenerationMode.EDIT)) {
            assertThrows(
                    BusinessException.class,
                    () -> task.modify("src/Old.vue", "old", "new"));
            task.directory();
            task.read("src/Old.vue");
            task.modify("src/Old.vue", "old", "new");
        }
        assertEquals(1, events.size());
        assertEquals(FileOperation.UPDATE, events.get(0).operation());
        assertTrue(events.get(0).content().contains("new"));
    }

    @Test
    void createModeCannotDeleteExistingFiles() {
        List<FileChangeEvent> events = new ArrayList<>();
        try (ProjectTask task =
                new ProjectTask(
                        Map.of("src/Old.vue", "<template>old</template>"),
                        progress -> {},
                        events::add,
                        GenerationMode.CREATE)) {
            assertThrows(BusinessException.class, () -> task.delete("src/Old.vue"));
        }
        assertTrue(events.isEmpty());
    }
}
