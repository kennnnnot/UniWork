import com.idongxia.uniwork.SendResult;
import com.idongxia.uniwork.UniWork;
import com.idongxia.uniwork.channel.CollaborationChannel;

/** Compiled and run with only the standalone JAR and JDK on the classpath. */
public final class StandaloneUsage {

    public static void run() {
        try (UniWork uniWork = UniWork.load()) {
            checkChannel(uniWork.wecom(), "wecom", "wecom-message");
            checkChannel(uniWork.dingtalk(), "dingtalk", "101");
            checkChannel(uniWork.feishu(), "feishu", "feishu-message");
        }
    }

    private static void checkChannel(
            CollaborationChannel channel, String platform, String messageId) {
        checkResult(channel.sendContent("staff-100", "审批提醒"), platform, messageId);
        checkResult(channel.sendCard(
                "staff-100", "采购审批", "项目等待处理", "https://example.com/tasks/1"),
                platform, messageId);
        if (!channel.loginUrl().contains("redirect_uri=")) {
            throw new AssertionError("Missing OAuth redirect URI for " + platform);
        }
    }

    private static void checkResult(SendResult result, String platform, String messageId) {
        if (!platform.equals(result.getPlatform()) || !messageId.equals(result.getMessageId())) {
            throw new AssertionError("Unexpected send result for " + platform);
        }
    }
}
