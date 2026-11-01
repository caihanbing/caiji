package app.caiji.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFeedbackToneTest {
    @Test fun passwordAndDamagedFileMessagesAreErrors() {
        assertEquals(FeedbackTone.ERROR, backupFeedbackTone("密码不正确，或备份文件已损坏"))
        assertEquals(FeedbackTone.ERROR, backupFeedbackTone("恢复过程超时"))
        assertEquals(FeedbackTone.ERROR, backupFeedbackTone("金额必须大于零", isModelError = true))
    }

    @Test fun successAndCancellationMessagesUseTheirOwnTones() {
        assertEquals(FeedbackTone.SUCCESS, backupFeedbackTone("加密备份已保存"))
        assertEquals(FeedbackTone.SUCCESS, backupFeedbackTone("账单已恢复"))
        assertEquals(FeedbackTone.NOTICE, backupFeedbackTone("已取消恢复，原数据未更改"))
    }
}
