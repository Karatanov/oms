package oms.umitaf.repository
import oms.umitaf.database.tables.InspectionPhotoTable
import oms.umitaf.domain.InspectionPhoto
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
class ExposedInspectionPhotoRepository:InspectionPhotoRepository {
 override fun list(reportId:Long)=transaction{InspectionPhotoTable.selectAll().filter{it[InspectionPhotoTable.reportId].value==reportId}.map(::map)}
 override fun get(reportId:Long,uuid:String)=transaction{InspectionPhotoTable.selectAll().firstOrNull{it[InspectionPhotoTable.reportId].value==reportId&&it[InspectionPhotoTable.uuid]==uuid}?.let(::map)}
 override fun create(photo:InspectionPhoto){transaction{InspectionPhotoTable.insert{it[uuid]=photo.uuid.toString();it[reportId]=photo.reportId;it[originalName]=photo.originalName;it[storagePath]=photo.storagePath;it[thumbnailPath]=photo.thumbnailPath;it[contentType]=photo.contentType;it[fileSizeBytes]=photo.fileSizeBytes;it[isMain]=photo.isMain}}}
 override fun setMain(reportId:Long,uuid:String)=transaction{if(get(reportId,uuid)==null) null else {InspectionPhotoTable.update({InspectionPhotoTable.reportId eq reportId}){it[isMain]=false};InspectionPhotoTable.update({(InspectionPhotoTable.reportId eq reportId) and (InspectionPhotoTable.uuid eq uuid)}){it[isMain]=true};get(reportId,uuid)}}
 override fun delete(reportId:Long,uuid:String)=transaction{InspectionPhotoTable.deleteWhere{(InspectionPhotoTable.reportId eq reportId) and (InspectionPhotoTable.uuid eq uuid)}>0}
 private fun map(r:org.jetbrains.exposed.v1.core.ResultRow)=InspectionPhoto(r[InspectionPhotoTable.id].value,UUID.fromString(r[InspectionPhotoTable.uuid]),r[InspectionPhotoTable.reportId].value,r[InspectionPhotoTable.originalName],r[InspectionPhotoTable.storagePath],r[InspectionPhotoTable.thumbnailPath],r[InspectionPhotoTable.contentType],r[InspectionPhotoTable.fileSizeBytes],r[InspectionPhotoTable.isMain])
}
