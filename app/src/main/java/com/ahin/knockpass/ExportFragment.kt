package com.ahin.knockpass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

class ExportFragment : Fragment() {

    private lateinit var etEmail: EditText
    private lateinit var btnExport: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_export, container, false).also { view ->
        etEmail   = view.findViewById(R.id.etEmail)
        btnExport = view.findViewById(R.id.btnExport)

        btnExport.setOnClickListener {
            val to = etEmail.text.toString().trim()
            if (to.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter recipient email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportCsvByEmail(to)
        }
    }

    // --- CSV 이메일 전송 로직 ----------------------
    private fun exportCsvByEmail(recipient: String) {
        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir == null || !dir.exists()) {
            Toast.makeText(requireContext(), "CSV directory not found", Toast.LENGTH_SHORT).show()
            return
        }
        val csvs = dir.listFiles { _, n -> n.endsWith(".csv") }
        if (csvs.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No CSV files to send", Toast.LENGTH_SHORT).show()
            return
        }

        // 최신 파일 선택
        val latest = csvs.maxByOrNull { it.lastModified() } ?: return
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            latest
        )

        // 이메일 인텐트 구성
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, "Sensor Data CSV")
            putExtra(Intent.EXTRA_TEXT, "첨부된 센서 데이터를 확인해주세요.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Send email via:"))
    }
}