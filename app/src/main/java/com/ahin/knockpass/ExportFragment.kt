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
import java.util.zip.*

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
            sendAllAsZip(toEmail)
        }

        return view
    }

    private fun sendAllAsZip(recipient: String) {
        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val csvFiles = dir?.listFiles { _, name -> name.endsWith(".csv") }

        if (csvFiles.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "보낼 CSV 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val zipFile = File(dir, "sensor_data_export.zip")
        try {
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                csvFiles.forEach { file ->
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().copyTo(zos)
                    zos.closeEntry()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "ZIP 생성 실패: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            zipFile
        )

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, "센서 데이터 ZIP")
            putExtra(Intent.EXTRA_TEXT, "첨부된 압축파일에서 센서 데이터를 확인해주세요.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(emailIntent, "이메일 앱 선택"))
    }
}