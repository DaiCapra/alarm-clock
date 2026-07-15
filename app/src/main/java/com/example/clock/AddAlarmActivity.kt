package com.example.clock

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.clock.audio.AlarmSounds
import com.example.clock.data.Alarm
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Create or edit an alarm: time, repeat days, label, ringtone, vibration, volume
 * and snooze.
 *
 * The alarm being edited lives in [AddAlarmViewModel], not here. The widgets are
 * inputs that push into it and Save reads only from it — when the two disagreed,
 * a rotation lost whichever half had no view to restore it from.
 */
@AndroidEntryPoint
class AddAlarmActivity : AppCompatActivity() {

    private val viewModel: AddAlarmViewModel by viewModels()

    private lateinit var hourPicker: NumberPicker
    private lateinit var minutePicker: NumberPicker
    private lateinit var dayChips: ChipGroup
    private lateinit var labelInput: TextInputEditText
    private lateinit var ringtoneValue: TextView
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var vibrateSwitch: MaterialSwitch
    private lateinit var snoozeInput: MaterialAutoCompleteTextView
    private lateinit var volumeSlider: Slider

    // Suppresses the push-back-into-the-ViewModel while binding its own value.
    private var binding = false

    // Short ringtone clip used to preview the volume level; stopped on leave.
    private val preview by lazy { RingtonePreview(this, volumeSlider) }

    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(
                it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
            )
        }
        viewModel.update { it.copy(ringtoneUri = uri?.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm)

        // Route the hardware volume keys to the alarm stream on this screen so
        // the "Alarm" volume popup shows consistently while setting the level.
        volumeControlStream = AudioManager.STREAM_ALARM

        applySystemBarInsetsAsPadding(findViewById(R.id.add_alarm_root))

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            if (viewModel.isEditing) setTitle(R.string.edit_alarm)
        }
        hourPicker = findViewById(R.id.hour_picker)
        minutePicker = findViewById(R.id.minute_picker)
        dayChips = findViewById(R.id.day_chips)
        labelInput = findViewById(R.id.label_input)
        ringtoneValue = findViewById(R.id.ringtone_value)
        enabledSwitch = findViewById(R.id.enabled_switch)
        vibrateSwitch = findViewById(R.id.vibrate_switch)
        snoozeInput = findViewById(R.id.snooze_input)
        volumeSlider = findViewById(R.id.volume_slider)

        setupTimePickers()
        buildDayChips()
        setupSnoozeOptions()
        setupVolumeSlider()

        findViewById<View>(R.id.ringtone_row).setOnClickListener { pickRingtone() }
        findViewById<MaterialButton>(R.id.save_button).setOnClickListener { onSave() }
        findViewById<MaterialButton>(R.id.cancel_button).setOnClickListener { finish() }

        enabledSwitch.setOnClickListener {
            viewModel.update { it.copy(isEnabled = enabledSwitch.isChecked) }
        }
        // Click fires only on user interaction (not programmatic binding), so
        // opening the screen never buzzes.
        vibrateSwitch.setOnClickListener {
            viewModel.update { it.copy(vibrate = vibrateSwitch.isChecked) }
            if (vibrateSwitch.isChecked) AlarmSounds.vibrateOnce(this)
        }

        observeDraft()
        observeSave()
    }

    /** Bind once, on the first loaded draft. Later updates come from the widgets
     *  themselves, so re-binding on every emission would fight the user's typing. */
    private fun observeDraft() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val draft = viewModel.draft.filterNotNull().first()
                bind(draft)
                // The ringtone is the one field with no widget of its own, so it
                // does need to track the draft.
                viewModel.draft.filterNotNull().collect { updateRingtoneLabel(it.ringtoneUri) }
            }
        }
    }

    private fun observeSave() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    if (state !is AddAlarmViewModel.SaveState.Saved) return@collect
                    // Replayed to a screen recreated mid-save, which is what
                    // makes a rotation there finish instead of duplicating.
                    val message = state.nextTrigger?.let {
                        alarmTriggerMessage(it - System.currentTimeMillis())
                    } ?: getString(R.string.saved_disabled)
                    Toast.makeText(this@AddAlarmActivity, message, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun bind(alarm: Alarm) = withoutFeedback {
        hourPicker.value = alarm.hour
        minutePicker.value = alarm.minute
        labelInput.setText(alarm.label)
        enabledSwitch.isChecked = alarm.isEnabled
        vibrateSwitch.isChecked = alarm.vibrate
        for (day in 0 until 7) {
            (dayChips.getChildAt(day) as Chip).isChecked = (alarm.repeatMask shr day) and 1 == 1
        }
        snoozeInput.setText("${alarm.snoozeMinutes} min", false)
        volumeSlider.value = alarm.volume.coerceIn(0, 100).toFloat()
        updateRingtoneLabel(alarm.ringtoneUri)
    }

    private inline fun withoutFeedback(block: () -> Unit) {
        binding = true
        try {
            block()
        } finally {
            binding = false
        }
    }

    private fun setupTimePickers() {
        hourPicker.setup(max = 23)
        minutePicker.setup(max = 59)
    }

    private fun NumberPicker.setup(max: Int) {
        minValue = 0
        maxValue = max
        wrapSelectorWheel = true
        setFormatter { String.format("%02d", it) }
    }

    private fun buildDayChips() {
        DAY_LABELS.forEachIndexed { index, label ->
            dayChips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                setOnClickListener { onDaysChanged() }
            })
            // A chip with no id is skipped by view-state save/restore. The
            // ViewModel is the real source of truth, but ids keep the widgets
            // honest across recreation (and give a11y something to hold).
            dayChips.getChildAt(index).id = View.generateViewId()
        }
    }

    private fun onDaysChanged() {
        if (binding) return
        var days = 0
        for (day in 0 until 7) {
            if ((dayChips.getChildAt(day) as Chip).isChecked) days = days or (1 shl day)
        }
        viewModel.update { it.copy(repeatDays = days) }
    }

    private fun setupSnoozeOptions() {
        snoozeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, SNOOZE_OPTIONS.map { "$it min" })
        )
        snoozeInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.update { it.copy(snoozeMinutes = SNOOZE_OPTIONS[position]) }
        }
    }

    private fun setupVolumeSlider() {
        volumeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.update { it.copy(volume = value.toInt()) }
        }
        // Preview the chosen level once the user releases the slider.
        volumeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                val draft = viewModel.draft.value ?: return
                preview.play(draft.ringtoneUri, slider.value.toInt())
            }
        })
    }

    override fun onStop() {
        super.onStop()
        preview.stop()
    }

    private fun pickRingtone() {
        val current = AlarmSounds.resolveRingtoneUri(viewModel.draft.value?.ringtoneUri)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        }
        ringtonePicker.launch(intent)
    }

    /** Fold the widgets whose value is only read at save time into the draft,
     *  then persist it. */
    private fun onSave() {
        // A NumberPicker keeps typed digits in an internal EditText and only
        // commits them to value on focus loss. Without this, typing a time and
        // tapping Save straight after stores the *previous* time, silently.
        hourPicker.clearFocus()
        minutePicker.clearFocus()

        val dayChecked = (0 until 7).map { (dayChips.getChildAt(it) as Chip).isChecked }
        viewModel.update { draft ->
            AlarmForm.buildAlarm(
                base = draft,
                hour = hourPicker.value,
                minute = minutePicker.value,
                label = labelInput.text?.toString().orEmpty(),
                dayChecked = dayChecked,
                vibrate = vibrateSwitch.isChecked,
                snoozeMinutes = AlarmForm.parseSnoozeMinutes(
                    snoozeInput.text.toString(), draft.snoozeMinutes
                ),
                volume = volumeSlider.value.toInt(),
                isEnabled = enabledSwitch.isChecked
            )
        }
        viewModel.save()
    }

    private fun updateRingtoneLabel(ringtoneUri: String?) {
        val uri = ringtoneUri?.let(Uri::parse)
        ringtoneValue.text = if (uri == null) {
            getString(R.string.default_ringtone)
        } else {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
                ?: getString(R.string.default_ringtone)
        }
    }

    companion object {
        const val EXTRA_EDIT_ID = "extra_edit_id"
        private val DAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S") // Sun..Sat
        private val SNOOZE_OPTIONS = listOf(5, 10, 15, 20, 30)
    }
}
