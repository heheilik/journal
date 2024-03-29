package com.example.journal.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.journal.*
import com.example.journal.adapters.LastPageAdapter
import com.example.journal.managers.WeekManager
import com.example.journal.parsers.LastPageParser
import com.example.journal.parsers.WeekPageParser
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.android.synthetic.main.activity_main_menu.*
import kotlinx.android.synthetic.main.fragment_journal.*
import kotlinx.android.synthetic.main.fragment_last_page.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileReader
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.HashMap

class MainMenuActivity : AppCompatActivity() {
    private val ROOT_DIRECTORY: String by lazy { filesDir.toString() }

    private val currentUser: String? by lazy {
        val currentUserFile = File("$ROOT_DIRECTORY/current_user.txt")
        if (!currentUserFile.exists()) null else FileReader(currentUserFile).readLines()[0]
    }
    private var userData = HashMap<String, String>()

    private val weekManager: WeekManager by lazy {
        if (userData["pupilUrl"] == null) startActivity(Intent(this, LoginActivity::class.java))
        WeekManager(ROOT_DIRECTORY, userData["pupilUrl"]!!, this)
    }
    private val weekPageParser: WeekPageParser by lazy { WeekPageParser() }

    private val firstDay = StructDay(0, 0, 0).apply { setCurrentDay() }
    private val posToDay = HashMap<Int, StructDay>()

    private val pageParseMutex = Mutex()

    private val datePickerDialog by lazy {
        val cal = Calendar.getInstance()
        DatePickerDialog(
                this,
                { _, year, month, day ->
                    var chosenDay = StructDay(0, 0, 0).apply {
                        setDayWithCalendar(GregorianCalendar(year, month, day))
                    }
                    var pos = Int.MAX_VALUE / 2
                    while (chosenDay < firstDay) {
                        chosenDay++
                        pos--
                    }
                    while (chosenDay > firstDay) {
                        chosenDay--
                        pos++
                    }
                    journalRecyclerView.stopScroll()
                    (journalRecyclerView.layoutManager as LinearLayoutManager?)
                            ?.scrollToPositionWithOffset(pos, Conversion.dpToPx(-48f, this))
                },
                cal[Calendar.YEAR], cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH]
        ).apply {
            datePicker.firstDayOfWeek = Calendar.MONDAY
            datePicker.minDate = GregorianCalendar(
                    YearData.FIRST_MONDAYS[0][0],
                    YearData.FIRST_MONDAYS[0][1] - 1,
                    YearData.FIRST_MONDAYS[0][2]
            ).timeInMillis
            datePicker.maxDate = GregorianCalendar(
                    YearData.FIRST_MONDAYS[3][0],
                    YearData.FIRST_MONDAYS[3][1] - 1,
                    YearData.FIRST_MONDAYS[3][2]
            ).apply {
                roll(Calendar.DAY_OF_WEEK, 5)
                roll(Calendar.WEEK_OF_YEAR, YearData.AMOUNTS_OF_WEEKS[3] - 1)
            }.timeInMillis
        }
    }

    var lpReadyOrProcessing = false

    inner class JournalRecyclerAdapter : RecyclerView.Adapter<JournalRecyclerAdapter.ViewHolder>() {
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val dateView: TextView
            val weekDayNameView: TextView
            val dayRootLayout: LinearLayout

            init {
                dateView = itemView.findViewById(R.id.date)
                weekDayNameView = itemView.findViewById(R.id.weekDay)
                dayRootLayout = itemView.findViewById(R.id.dayRootLayout)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(
                    R.layout.view_day, parent, false
            ))
        }

        override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
            if (posToDay[pos] == null) {
                val day = firstDay.copy().apply { plus(pos - Int.MAX_VALUE / 2) }
                posToDay[pos] = day
            }

            val quarter = posToDay[pos]!!.quarter
            val week = posToDay[pos]!!.week
            val weekDay = posToDay[pos]!!.weekDay

            if (quarter < 0 || quarter > 3) {
                holder.dateView.text = ""
                holder.weekDayNameView.text = ""
                return
            }

            if (weekManager.datesData[quarter][week][weekDay].dateString == "") {
                weekManager.datesData[quarter][week][weekDay].yearDay = posToDay[pos]
                weekManager.datesData[quarter][week][weekDay].generateStrings()
            }
            holder.dateView.text = weekManager.datesData[quarter][week][weekDay].dateString
            holder.weekDayNameView.text = weekManager.datesData[quarter][week][weekDay].weekDayString

            when (weekManager.weekStates[quarter][week]) {
                PageState.EMPTY -> {
                    runOnUiThread {
                        holder.dayRootLayout.addView(TextView(this@MainMenuActivity).apply {
                            text = "Загрузка..."
                            gravity = Gravity.CENTER
                        })
                    }
                    weekManager.weekStates[quarter][week] = PageState.PROCESSING
                    GlobalScope.launch {
                        updatePage(quarter, week)
                        weekManager.weekStates[quarter][week] = PageState.READY
                        var day = firstDay.copy()
                        var position = Int.MAX_VALUE / 2
                        while (day.quarter < quarter) {
                            day++
                            position++
                        }
                        while (day.quarter > quarter) {
                            day--
                            position--
                        }
                        while (day.week < week) {
                            day++
                            position++
                        }
                        while (day.week > week) {
                            day--
                            position--
                        }
                        while (day.weekDay > 0) {
                            day--
                            position--
                        }

                        runOnUiThread {
                            repeat(6) { journalRecyclerView.adapter?.notifyItemChanged(position++) }
                        }
                    }
                }
                PageState.PROCESSING -> {
                    runOnUiThread {
                        holder.dayRootLayout.addView(TextView(this@MainMenuActivity).apply {
                            text = "Загрузка..."
                            gravity = Gravity.CENTER
                        })
                    }
                }
                PageState.READY -> {
                    while (holder.dayRootLayout.childCount > 1) holder.dayRootLayout.removeViewAt(holder.dayRootLayout.childCount - 1)
                    for (i in 0 until weekManager.lessonsViews[quarter][week][weekDay].size) {
                        weekManager.lessonsViews[quarter][week][weekDay][i].let {
                            if (it.parent != null) (it.parent as LinearLayout).removeView(it)
                            holder.dayRootLayout.addView(it)
                        }
                    }
                }
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            while (holder.dayRootLayout.childCount > 1) holder.dayRootLayout.removeViewAt(holder.dayRootLayout.childCount - 1)
        }

        override fun getItemCount(): Int = Int.MAX_VALUE
    }

    suspend fun updatePage(quarter: Int, week: Int) {
        val downloader = PageDownloader(
                ROOT_DIRECTORY,
                userData["sessionid"]!!,
                quarter, week,
                weekManager.weekLinks[quarter][week]
        )

        if (!downloader.downloadPage()) return
        pageParseMutex.withLock {
            weekPageParser.parsePage(
                    "$ROOT_DIRECTORY/pages/q${quarter}w${week}.html",
                    "$ROOT_DIRECTORY/data/q${quarter}w${week}.txt"
            )
        }
        weekManager.createLessonViews("$ROOT_DIRECTORY/data/q${quarter}w${week}.txt", quarter, week, layoutInflater)
    }

    private fun updateLastPage() {
        lpReadyOrProcessing = true
        val downloader = PageDownloader(
                ROOT_DIRECTORY,
                userData["sessionid"]!!,
                4, 0,
                weekManager.weekLinks[4][0]
        )
        val lpParser = LastPageParser()

        if (!downloader.downloadPage()) return
        lpParser.parsePage(
                "$ROOT_DIRECTORY/pages/q4w0.html",
                "$ROOT_DIRECTORY/data/lp.txt"
        )

        var index = 0
        var lesson: LessonYearMarks
        val text = FileReader("$ROOT_DIRECTORY/data/lp.txt").readText()
        while (index < text.length) {
            lesson = LessonYearMarks("", "", "", "", "", "")
            try {
                while (text[index] != '>') lesson.lesson += text[index++]
                index++
                while (text[index] != '>') lesson.mark1Q += text[index++]
                index++
                while (text[index] != '>') lesson.mark2Q += text[index++]
                index++
                while (text[index] != '>') lesson.mark3Q += text[index++]
                index++
                while (text[index] != '>') lesson.mark4Q += text[index++]
                index++
                while (text[index] != '>') lesson.markYear += text[index++]
                index++
            } catch (e: Exception) { break }
            if (
                    lesson.lesson != " " && lesson.lesson != "" && (
                            lesson.mark1Q != "-" ||
                            lesson.mark2Q != "-" ||
                            lesson.mark3Q != "-" ||
                            lesson.mark4Q != "-"
                    )
            ) {
                if (lesson.markYear == "-") {
                    var amountOfMarks = 0
                    val marks = arrayListOf(0f, 0f, 0f, 0f)
                    try {
                        marks[0] = lesson.mark1Q.toFloat()
                        amountOfMarks++
                    } catch (e: NumberFormatException) {}
                    try {
                        marks[1] = lesson.mark2Q.toFloat()
                        amountOfMarks++
                    } catch (e: NumberFormatException) {}
                    try {
                        marks[2] = lesson.mark3Q.toFloat()
                        amountOfMarks++
                    } catch (e: NumberFormatException) {}
                    try {
                        marks[3] = lesson.mark4Q.toFloat()
                        amountOfMarks++
                    } catch (e: NumberFormatException) {}

                    lesson.markYear =
                            (if (amountOfMarks == 0) "-"
                            else "~" + with(DecimalFormat("#.##")) {
                                format(marks.sum() / amountOfMarks).toString()
                            })
                }
                (lastPageRecyclerView.adapter as LastPageAdapter).data.add(lesson)
            }
        }
        runOnUiThread {
            (lastPageLoadingText.parent as ViewGroup?)?.removeView(lastPageLoadingText)
            lastPageRecyclerView.adapter?.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        readUserDataParams()
        setContentView(R.layout.activity_main_menu)

        switcher.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        supportFragmentManager.beginTransaction().show(journalFragment).commit()
                        datePickButton.isClickable = true
                        datePickButton.show()
                        switcher.getTabAt(0)?.text = "На сегодня..."
                    }
                    1 -> {
                        supportFragmentManager.beginTransaction().show(lpFragment).commit()
                        if (!lpReadyOrProcessing) GlobalScope.launch { updateLastPage() }
                    }
                    2 -> supportFragmentManager.beginTransaction().show(settingsFragment).commit()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        supportFragmentManager.beginTransaction().hide(journalFragment).commit()
                        datePickButton.isClickable = false
                        datePickButton.hide()
                        switcher.getTabAt(0)?.text = "Дневник"
                        journalRecyclerView.stopScroll()
                    }
                    1 -> supportFragmentManager.beginTransaction().hide(lpFragment).commit()
                    2 -> supportFragmentManager.beginTransaction().hide(settingsFragment).commit()
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    journalRecyclerView.stopScroll()
                    (journalRecyclerView.layoutManager as LinearLayoutManager?)
                            ?.scrollToPositionWithOffset(Int.MAX_VALUE / 2, Conversion.dpToPx(-48f, this@MainMenuActivity))
                }
            }
        })
        logOutButton.setOnClickListener {
            File("$ROOT_DIRECTORY/users/$currentUser.txt").delete()
            File("$ROOT_DIRECTORY/current_user.txt").delete()
            startActivity(Intent(this, LoginActivity::class.java))
        }
        datePickButton.setOnClickListener {
            datePickerDialog.show()
        }

        userNameTextView.text = userData["realName"]

        supportFragmentManager.beginTransaction()
                .hide(lpFragment)
                .hide(settingsFragment)
                .show(journalFragment)
                .commit()

        journalRecyclerView.layoutManager = LinearLayoutManager(this)
        journalRecyclerView.adapter = JournalRecyclerAdapter()
        (journalRecyclerView.layoutManager as LinearLayoutManager?)
                ?.scrollToPositionWithOffset(Int.MAX_VALUE / 2, Conversion.dpToPx(-48f, this))

        lastPageRecyclerView.layoutManager = LinearLayoutManager(this)
        lastPageRecyclerView.adapter = LastPageAdapter()
    }

    override fun onBackPressed() {}

    private fun readUserDataParams() {
        userData.clear()
        val paramsReader = FileReader("$ROOT_DIRECTORY/users/$currentUser.txt")
        val paramsList = paramsReader.readLines()
        for (str in paramsList) {
            var it = 0
            var parameterName = ""
            var parameterValue = ""

            while (str[it] != ':') parameterName += str[it++]
            it += 2
            while (it < str.length) parameterValue += str[it++]

            userData[parameterName] = parameterValue
        }
    }
}