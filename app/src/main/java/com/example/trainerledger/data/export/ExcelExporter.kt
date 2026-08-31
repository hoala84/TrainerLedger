package com.example.trainerledger.data.export

import com.example.trainerledger.domain.model.Client
import com.example.trainerledger.domain.model.Payment
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.domain.model.WorkoutType
import com.example.trainerledger.util.DateUtils
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Минимальный XLSX (Office Open XML) без Apache POI — стабильно работает на Android.
 * Строки — даты периода, столбцы — клиенты, в ячейках оплаты и тренировки.
 */
object ExcelExporter {

    fun write(
        output: OutputStream,
        from: Long,
        to: Long,
        clients: List<Client>,
        payments: List<Payment>,
        workouts: List<Workout>,
    ) {
        val orderedClients = clients.sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }))
        val days = DateUtils.daysInRange(from, to)
        val paymentsByKey = payments.groupBy { it.clientId to DateUtils.startOfDay(it.date) }
        val workoutsByKey = workouts.groupBy { it.clientId to DateUtils.startOfDay(it.date) }

        val rows = mutableListOf<List<String>>()
        rows += listOf("Дата") + orderedClients.map { it.displayName }
        for (day in days) {
            val row = mutableListOf(DateUtils.formatShort(day))
            for (client in orderedClients) {
                val key = client.id to day
                row += cellText(paymentsByKey[key].orEmpty(), workoutsByKey[key].orEmpty())
            }
            rows += row
        }

        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(CONTENT_TYPES.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(RELS.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(workbookXml().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(WORKBOOK_RELS.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(STYLES.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml(rows).toByteArray())
            zip.closeEntry()
        }
    }

    private fun cellText(payments: List<Payment>, workouts: List<Workout>): String {
        if (payments.isEmpty() && workouts.isEmpty()) return ""
        val parts = mutableListOf<String>()
        payments.forEach { payment ->
            parts += "Оплата ${formatMoney(payment.amount)} (${payment.workoutCount} тр.)"
        }
        workouts.forEach { workout ->
            val typeLabel = when (workout.type) {
                WorkoutType.PAID -> "тренировка"
                WorkoutType.DEBT -> "в долг"
                WorkoutType.GIFT -> "подарок"
            }
            val comment = workout.comment.trim().takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""
            parts += "$typeLabel$comment"
        }
        return parts.joinToString("\n")
    }

    private fun formatMoney(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            "${amount.toLong()} ₽"
        } else {
            String.format("%.2f ₽", amount)
        }
    }

    private fun sheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetData>""")
        rows.forEachIndexed { rIndex, row ->
            val rowNum = rIndex + 1
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIndex, value ->
                val ref = cellRef(cIndex, rowNum)
                val style = if (rIndex == 0) """ s="1"""" else ""
                if (value.isEmpty()) {
                    sb.append("""<c r="$ref"$style/>""")
                } else {
                    sb.append("""<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${escape(value)}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun cellRef(columnIndex: Int, rowNum: Int): String {
        var n = columnIndex + 1
        val letters = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            letters.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return letters.reverse().toString() + rowNum
    }

    private fun escape(text: String): String = buildString {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    private fun workbookXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Период" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>""".trimIndent()

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="2">
    <xf xfId="0"/>
    <xf xfId="0" fontId="1" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
}
