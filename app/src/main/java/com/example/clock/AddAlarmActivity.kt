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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch

/** Create or edit an alarm: time, repeat days, label, ringtone, vibration and snooze. */
@AndroidEntryPoint
class AddAlarmActivity : AppCompatActivity() {

    private val viewModel: AddAlarmViewModel by viewModels()

    // Working copy of the alarm being edited; the UI mutates this and Save persists it.
    private lateinit var draft: Alarm

    private lateinit var hourPicker: NumberPicker
    private lateinit var minutePicker: NumberPicker
    private lateinit var dayChips: ChipGroup
    private lateinit var labelInput: TextInputEditText
    private lateinit var ringtoneValue: TextView
    private lateinit var vibrateSwitch: MaterialSwitch
    private lateinit var snoozeInput: MaterialAutoCompleteTextView
    private lateinit var volumeSlider: Slider

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
        draft = draft.copy(ringtoneUri = uri?.toString())
        updateRingtoneLabel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm)

        // Seeded from the user's last-used defaults; the edit path replaces it.
        draft = viewModel.newAlarmDraft()

        // Route the hardware volume keys to the alarm stream on this screen so
        // the "Alarm" volume popup shows consistently while setting the level.
        volumeControlStream = AudioManager.STREAM_ALARM

        applySystemBarInsetsAsPadding(findViewById(R.id.add_alarm_root))

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        hourPicker = findViewById(R.id.hour_picker)
        minutePicker = findViewById(R.id.minute_picker)
        dayChips = findViewById(R.id.day_chips)
        labelInput = findViewById(R.id.label_input)
        ringtoneValue = findViewById(R.id.ringtone_value)
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
        // Click fires only on user interaction (not programmatic bindDraft),
        // so opening the screen never buzzes.
        vibrateSwitch.setOnClickListener {
            if (vibrateSwitch.isChecked) AlarmSounds.vibrateOnce(this)
        }

        val editId = intent.getIntExtra(EXTRA_EDIT_ID, 0)
        if (editId != 0) {
            findViewById<MaterialToolbar>(R.id.toolbar).setTitle(R.string.edit_alarm)
            lifecycleScope.launch {
                viewModel.load(editId)?.let { draft = it }
                bindDraft()
            }
        } else {
            bindDraft()
        }
    }

    private fun bindDraft() {
        hourPicker.value = draft.hour
        minutePicker.value = draft.minute
        labelInput.setText(draft.label)
        vibrateSwitch.isChecked = draft.vibrate
        for (day in 0 until 7) {
            (dayChips.getChildAt(day) as Chip).isChecked = (draft.repeatDays shr day) and 1 == 1
        }
        snoozeInput.setText("${draft.snoozeMinutes} min", false)
        volumeSlider.value = draft.volume.coerceIn(0, 100).toFloat()
        updateRingtoneLabel()
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
        DAY_LABELS.forEach { label ->
            dayChips.addView(Chip(this).apply {
                text = label
                isCheckable = true
            })
        }
    }

    private fun setupSnoozeOptions() {
        val labels = SNOOZE_OPTIONS.map { "$it min" }
        snoozeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
    }

    private fun setupVolumeSlider() {
        volumeSlider.addOnChangeListener { _, value, _ ->
            draft = draft.copy(volume = value.toInt())
        }
        // Preview the chosen level once the user releases the slider.
        volumeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) =
                preview.play(draft.ringtoneUri, slider.value.toInt())
        })
    }

    override fun onStop() {
        super.onStop()
        preview.stop()
    }

    private fun pickRingtone() {
        val current = AlarmSounds.resolveRingtoneUri(draft.ringtoneUri)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        }
        ringtonePicker.launch(intent)
    }

    private fun onSave() {
        val dayChecked = (0 until 7).map { (dayChips.getChildAt(it) as Chip).isChecked }
        val alarm = AlarmForm.buildAlarm(
            base = draft,
            hour = hourPicker.value,
            minute = minutePicker.value,
            label = labelInput.text?.toString().orEmpty(),
            dayChecked = dayChecked,
            vibrate = vibrateSwitch.isChecked,
            snoozeMinutes = AlarmForm.parseSnoozeMinutes(
                snoozeInput.text.toString(), draft.snoozeMinutes
            ),
            volume = volumeSlider.value.toInt()
        )
        viewModel.save(alarm) { nextTrigger ->
            if (nextTrigger != null) {
                val remaining = nextTrigger - System.currentTimeMillis()
                Toast.makeText(this, alarmTriggerMessage(remaining), Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private fun updateRingtoneLabel() {
        val uri = draft.ringtoneUri?.let(Uri::parse)
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
