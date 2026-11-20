package com.tosker.sync

import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import java.io.ByteArrayOutputStream

/**
 * 앱 설정(단축버튼·목록 버튼 설정)을 Google Drive의 앱 전용 숨김 폴더
 * (appDataFolder)에 저장해 기기 간에 동기화한다.
 *
 * appDataFolder는 사용자의 Drive 화면에 보이지 않고 이 앱만 접근할 수 있다.
 */
class DriveSettingsSync(private val drive: Drive) {

    /** 저장된 설정 JSON을 가져온다. 아직 올린 적이 없으면 null. */
    fun download(): String? {
        val fileId = findFileId() ?: return null
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        return out.toString(Charsets.UTF_8.name())
    }

    /** 설정 JSON을 올린다. 파일이 있으면 덮어쓰고 없으면 새로 만든다. */
    fun upload(json: String) {
        val content = ByteArrayContent(MIME_TYPE, json.toByteArray(Charsets.UTF_8))
        val existingId = findFileId()

        if (existingId == null) {
            val metadata = DriveFile().apply {
                name = FILE_NAME
                parents = listOf(APP_DATA_FOLDER)
            }
            drive.files().create(metadata, content).setFields("id").execute()
        } else {
            // 수정할 때는 parents를 넣으면 안 된다(이동으로 해석됨).
            drive.files().update(existingId, DriveFile(), content).execute()
        }
    }

    private fun findFileId(): String? =
        drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$FILE_NAME' and trashed = false")
            .setFields("files(id, modifiedTime)")
            .setOrderBy("modifiedTime desc")
            .execute()
            .files
            ?.firstOrNull()
            ?.id

    private companion object {
        const val APP_DATA_FOLDER = "appDataFolder"
        const val FILE_NAME = "tosker_settings.json"
        const val MIME_TYPE = "application/json"
    }
}
