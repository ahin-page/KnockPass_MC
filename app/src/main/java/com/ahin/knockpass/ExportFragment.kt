package com.ahin.knockpass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment


class ExportFragment : Fragment() {

    private lateinit var exportButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_export, container, false)

        exportButton = view.findViewById(R.id.btn_export_csv)
        exportButton.setOnClickListener {
            sendEmailWithCSV()
        }

        return view
    }

    private fun sendEmailWithCSV() {
        // 최신 sensor_data_*.csv 파일 찾기
        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir == null || !dir.exists()) {
            Toast.makeText(requireContext(), "CSV 파일 디렉토리를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val csvFiles = dir.listFiles { _, name ->
            name.endsWith(".csv")
        }

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
            putExtra(Intent.EXTRA_SUBJECT, "Sensor Data CSV")
            putExtra(Intent.EXTRA_TEXT, "첨부된 센서 데이터를 확인해주세요.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(emailIntent, "Send email using:"))
    }
}