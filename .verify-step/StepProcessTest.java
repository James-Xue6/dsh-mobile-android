import com.dsh.mobile.model.StepProcess;
import java.util.Arrays;

/** StepProcess 回归：判据/文案必须与 PC 端 app.asar 里的 activity() + stepProcess.* 一致。 */
public class StepProcessTest {
    static int fail = 0;
    static void eq(String what, String want, String got) {
        if (!want.equals(got)) { System.out.println("FAIL " + what + ": want=" + want + " got=" + got); fail++; }
        else System.out.println("ok   " + what + " -> " + got);
    }
    public static void main(String[] a) {
        // activity(): app.asar:399154-399177
        eq("read", "read", StepProcess.activity("read"));
        eq("read_image", "readImage", StepProcess.activity("read_image"));
        eq("grep", "search", StepProcess.activity("grep"));
        eq("glob", "search", StepProcess.activity("glob"));
        eq("glob_x_inspect", "search", StepProcess.activity("foo_inspect"));
        eq("write", "write", StepProcess.activity("write"));
        eq("edit", "edit", StepProcess.activity("edit"));
        eq("apply_patch", "edit", StepProcess.activity("apply_patch"));
        eq("pwsh", "commands", StepProcess.activity("pwsh"));
        eq("bash", "commands", StepProcess.activity("bash"));
        eq("terminal_x", "commands", StepProcess.activity("terminal_open"));
        eq("run_code", "code", StepProcess.activity("run_code"));
        eq("web_search", "webSearch", StepProcess.activity("web_search"));
        eq("web_fetch", "webFetch", StepProcess.activity("web_fetch"));
        eq("subagent", "subagents", StepProcess.activity("subagent"));
        eq("subagent_fork", "subagents", StepProcess.activity("subagent_fork"));
        eq("todo_write", "plan", StepProcess.activity("todo_write"));
        eq("create_goal", "plan", StepProcess.activity("create_goal"));
        eq("ask_user_question", "questions", StepProcess.activity("ask_user_question"));
        eq("unknown", "tools", StepProcess.activity("whatever"));
        eq("empty", "tools", StepProcess.activity(""));

        // 单类别
        eq("title[pwsh]", "执行了命令", StepProcess.title(Arrays.asList("commands")));
        // 两类：joinTwo + sharedPrefix 规则
        eq("title[read,commands]", "已读取文件并执行了命令",
                StepProcess.title(Arrays.asList("read", "commands")));
        eq("title[read,search]", "已读取文件并搜索代码",
                StepProcess.title(Arrays.asList("read", "search")));
        // 三类
        eq("title[read,commands,edit]", "已读取文件，执行了命令，修改了文件",
                StepProcess.title(Arrays.asList("read", "commands", "edit")));
        // 四类 -> 末尾「等」
        eq("title[read,commands,edit,search]", "已读取文件，执行了命令，修改了文件等",
                StepProcess.title(Arrays.asList("read", "commands", "edit", "search")));
        // 排序：次数多的在前；次数相同按首次出现
        eq("rank by count", "执行了命令并已读取文件",
                StepProcess.title(Arrays.asList("read", "commands", "commands")));
        eq("tie keeps first", "已读取文件并执行了命令",
                StepProcess.title(Arrays.asList("read", "commands")));
        // 运行中
        eq("running commands", "正在运行命令", StepProcess.runningLabel(StepProcess.activity("pwsh")));
        eq("running read", "正在读取文件", StepProcess.runningLabel("read"));
        eq("done unknown", "已调用工具", StepProcess.doneLabel("tools"));

        System.out.println(fail == 0 ? "\nALL PASS" : "\n" + fail + " FAILED");
        if (fail != 0) System.exit(1);
    }
}
