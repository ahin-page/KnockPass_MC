package com.ahin.knockpass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

class ExportFragment : Fragment() {

    private lateinit var etEmail: EditText
    private lateinit var btnExport: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_export, container, false)

        etEmail = view.findViewById(R.id.etEmail)
        btnExport = view.findViewById(R.id.btnExport)

        btnExport.setOnClickListener {
            val toEmail = etEmail.text.toString().trim()
            if (toEmail.isEmpty()) {
                Toast.makeText(requireContext(), "이메일 주소를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendLatestCSV(toEmail)
        }

        return view
    }

    private fun sendLatestCSV(recipient: String) {
        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val csvFiles = dir?.listFiles { _, name -> name.endsWith(".csv") }

        if (csvFiles.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "보낼 CSV 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val latestFile = csvFiles.maxByOrNull { it.lastModified() } ?: return
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            latestFile
        )

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, "센서 데이터 CSV")
            putExtra(Intent.EXTRA_TEXT, "첨부된 센서 데이터를 확인해주세요.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(emailIntent, "이메일 앱 선택"))
    }
}