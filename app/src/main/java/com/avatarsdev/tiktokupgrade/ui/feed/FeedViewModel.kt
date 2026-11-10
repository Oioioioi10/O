package com.avatarsdev.tiktokupgrade.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avatarsdev.tiktokupgrade.TikTokUpgradeApp
import com.avatarsdev.tiktokupgrade.data.db.FeedProfileEntity
import com.avatarsdev.tiktokupgrade.data.db.InboxClipEntity
import com.avatarsdev.tiktokupgrade.domain.feed.ClipScore
import com.avatarsdev.tiktokupgrade.domain.feed.ClipScorer
import com.avatarsdev.tiktokupgrade.domain.feed.FeedPlanner
import com.avatarsdev.tiktokupgrade.domain.feed.FeedRules
import com.avatarsdev.tiktokupgrade.domain.feed.PlanSlot
import com.avatarsdev.tiktokupgrade.domain.feed.PromptParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val prompt: String = "",
    val rules: FeedRules? = null,
    val plan: List<PlanSlot> = emptyList(),
    val parsing: Boolean = false,
    val notice: String? = null,
)

class FeedViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as TikTokUpgradeApp).container
    private val db = container.db

    private val _state = MutableStateFlow(FeedUiState())
    val state = _state.asStateFlow()

    val aiConfigured: StateFlow<Boolean> = container.settings.state
        .map { it.aiConfigured }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.settings.state.value.aiConfigured)

    val profiles: StateFlow<List<FeedProfileEntity>> = db.feedProfileDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shared clips ranked against the current rules. */
    val inbox: StateFlow<List<Pair<InboxClipEntity, ClipScore>>> =
        combine(db.inboxDao().observeAll(), _state) { clips, st ->
            val rules = st.rules
            if (rules == null) clips.map { it to ClipScore(0, null, null, null, emptyList(), "ยังไม่ได้ตั้งค่าฟีด") }
            else ClipScorer.rank(clips, { it.text + " " + (it.url ?: "") }, rules)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _checkerText = MutableStateFlow("")
    val checkerText = _checkerText.asStateFlow()

    init {
        val s = container.settings.state.value
        if (s.lastPrompt.isNotBlank()) {
            _state.value = FeedUiState(prompt = s.lastPrompt)
            applyRules(PromptParser.parse(s.lastPrompt), null)
        }
    }

    fun setPrompt(p: String) { _state.value = _state.value.copy(prompt = p) }
    fun setChecker(t: String) { _checkerText.value = t }
    fun score(text: String): ClipScore? = _state.value.rules?.let { ClipScorer.score(text, it) }

    fun analyze(useAi: Boolean) {
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty()) {
            _state.value = _state.value.copy(notice = "พิมพ์สิ่งที่อยากดูก่อน เช่น Roblox 50%")
            return
        }
        container.settings.update { it.copy(lastPrompt = prompt) }
        val s = container.settings.state.value
        if (!(useAi && s.aiConfigured)) {
            applyRules(PromptParser.parse(prompt), null)
            return
        }
        _state.value = _state.value.copy(parsing = true, notice = null)
        viewModelScope.launch {
            try {
                val rules = container.llm.parse(prompt, s.aiBaseUrl, s.aiApiKey, s.aiModel)
                applyRules(rules, "วิเคราะห์ด้วย ${s.aiModel}")
            } catch (e: Exception) {
                applyRules(PromptParser.parse(prompt), "AI ภายนอกใช้ไม่ได้ (${e.message?.take(80)}) — ใช้ตัววิเคราะห์ในเครื่องแทน")
            }
        }
    }

    private fun applyRules(rules: FeedRules, notice: String?) {
        _state.value = _state.value.copy(
            rules = rules,
            plan = FeedPlanner.plan(rules, 20),
            parsing = false,
            notice = notice,
        )
    }

    fun saveProfile(name: String) {
        val st = _state.value
        val rules = st.rules ?: return
        viewModelScope.launch {
            db.feedProfileDao().insert(
                FeedProfileEntity(
                    name = name.ifBlank { rules.include.take(2).joinToString(" + ") { it.label }.ifBlank { "โปรไฟล์ฟีด" } },
                    prompt = st.prompt,
                    rulesJson = FeedRules.toJson(rules),
                    engine = rules.engine,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            _state.value = _state.value.copy(notice = "บันทึกโปรไฟล์แล้ว")
        }
    }

    fun loadProfile(p: FeedProfileEntity) {
        val rules = FeedRules.fromJson(p.rulesJson) ?: PromptParser.parse(p.prompt)
        container.settings.update { it.copy(lastPrompt = p.prompt, activeProfileId = p.id) }
        _state.value = _state.value.copy(prompt = p.prompt)
        applyRules(rules, "ใช้โปรไฟล์ \"${p.name}\"")
    }

    fun deleteProfile(id: Long) = viewModelScope.launch { db.feedProfileDao().delete(id) }

    fun addToInbox(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = Regex("https?://\\S+").find(t)?.value
        viewModelScope.launch {
            db.inboxDao().insert(InboxClipEntity(text = t, url = url, addedAt = System.currentTimeMillis()))
            _checkerText.value = ""
        }
    }

    fun deleteInbox(id: Long) = viewModelScope.launch { db.inboxDao().delete(id) }
    fun clearInbox() = viewModelScope.launch { db.inboxDao().clear() }
    fun dismissNotice() { _state.value = _state.value.copy(notice = null) }
}
