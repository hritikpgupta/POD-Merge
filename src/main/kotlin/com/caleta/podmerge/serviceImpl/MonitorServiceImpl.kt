package com.caleta.podmerge.serviceImpl


import com.caleta.podmerge.service.MonitoringService
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfWriter
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.Executors
import java.util.function.Consumer


@EnableScheduling
@Service
class MonitorServiceImpl : MonitoringService {
    private val LOG: Logger = LoggerFactory.getLogger(MonitorServiceImpl::class.java)

    @Autowired
    private lateinit var watchService: WatchService

    @Value("\${monitor-folder-path}")
    private lateinit var monitorFolderPath: String

    @Value("\${destination-path}")
    private lateinit var destinationPath: String

    @Value("\${temp-path}")
    private lateinit var tempPath: String

    @EventListener(value = [ApplicationReadyEvent::class], condition = "!@environment.acceptsProfiles('test')")
    override fun startMonitoring() {
        var key: WatchKey
        LOG.info("Started Monitoring Folder")
        try {
            while (watchService.take().also { key = it } != null) {
                sleep(500)
                key.pollEvents().forEach {
                    if (it != null) {
                        if(it.context() != null){
                            processFile(it.context().toString())
                        }else{
                            LOG.info("Context is null")
                        }
                    }else{
                        LOG.info("WatchEvent is null")
                    }
                }
                key.reset()
            }
        } catch (e: Exception) {
            LOG.error("Error detected while monitoring folder.")
            LOG.error(e.message)

        }
    }

    private fun processFile(docName: String) {
        LOG.info("Processing file : $docName")
        val folderName = docName.substring(0, docName.indexOf(".")).split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[0].split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].trim()
        if (docName.contains(".pdf")) {
            sleep(100)
            FileUtils.copyFile(
                File("$monitorFolderPath\\${docName}"), File("$destinationPath\\SHP-${folderName}.pdf")
            )
            return
        }
        createFolder(folderName)
        sleep(100)
        FileUtils.copyFile(
            File("$monitorFolderPath\\${docName}"), File("$tempPath\\$folderName\\${docName}")
        )
        sleep(500)
        createPDF(folderName, folderName)
    }

    private fun createFolder(folderName: String) {
        if (!File("$tempPath\\$folderName").exists()) {
            File("$tempPath\\$folderName").mkdirs()
        }
    }

    private fun createPDF(folderName: String, fileName: String) {
        LOG.info("Creating PDF $fileName.pdf")
        try {
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream("$destinationPath\\SHP-${fileName}.pdf"))
            document.open()
            File("$tempPath\\${folderName}").listFiles()?.forEach { f ->
                if (f.isFile && !f.path.contains(".pdf")) {
                    val img = Image.getInstance(f.path)
                    val scaleRatio = calculateScaleRatio(document, img)
                    if (scaleRatio < 1f) {
                        img.scalePercent(scaleRatio * 100f)
                    }
                    document.add(img)
                }
            }
            document.close()
        } catch (e: Exception) {
            LOG.info("Error creating PDF $fileName.pdf")
            LOG.error(e.message)
        }
    }

    private fun calculateScaleRatio(doc: Document, image: Image): Float {
        var scaleRatio: Float
        val imageWidth = image.width
        val imageHeight = image.height
        if (imageWidth > 0 && imageHeight > 0) {
            val pageSize: Rectangle = doc.pageSize
            val pageWidth: Float = pageSize.width - doc.leftMargin() - doc.rightMargin()
            scaleRatio = pageWidth / imageWidth
            val pageHeight: Float = pageSize.height - doc.topMargin() - doc.bottomMargin()
            val heightScaleRatio = pageHeight / imageHeight
            if (heightScaleRatio < scaleRatio) {
                scaleRatio = heightScaleRatio
            }
            if (scaleRatio > 1f) {
                scaleRatio = 1f
            }
        } else {
            scaleRatio = 1f
        }
        return scaleRatio
    }

    @Scheduled(cron = "0 0 12 ? * SUN")
    override fun clearTemp() {
        LOG.info("Clearing Temp Folder")
        File("$tempPath\\").listFiles()?.forEach {
            try {
                FileUtils.deleteDirectory(it)
            } catch (e: Exception) {
                LOG.error(e.message)
            }
        }

    }

}