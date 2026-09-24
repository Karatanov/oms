package oms.umitaf.repository
import oms.umitaf.database.tables.InspectionPhotoTable
import oms.umitaf.database.tables.InspectionReportTable
import oms.umitaf.domain.InspectionPhoto
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
class ExposedInspectionPhotoRepository:InspectionPhotoRepository {
 override fun list(reportId:Long)=transaction{InspectionPhotoTable.selectAll().where{InspectionPhotoTable.reportId eq reportId}.map(::map)}
 override fun get(reportId:Long,uuid:String)=transaction{InspectionPhotoTable.selectAll().where{(InspectionPhotoTable.reportId eq reportId) and (InspectionPhotoTable.uuid eq uuid)}.limit(1).firstOrNull()?.let(::map)}
 override fun create(photo:InspectionPhoto){transaction{
  require(InspectionReportTable.select(InspectionReportTable.id).where { InspectionReportTable.id eq photo.reportId }.forUpdate().firstOrNull() != null) { "Inspection report not found." }
  val existing = InspectionPhotoTable.select(InspectionPhotoTable.isMain).where { InspectionPhotoTable.reportId eq photo.reportId }.forUpdate().toList()
  require(existing.size < 30) { "An inspection report can contain no more than 30 photos." }
  val main = existing.none { it[InspectionPhotoTable.isMain] }
  InspectionPhotoTable.insert{it[uuid]=photo.uuid.toString();it[reportId]=photo.reportId;it[originalName]=photo.originalName;it[storagePath]=photo.storagePath;it[thumbnailPath]=photo.thumbnailPath;it[contentType]=photo.contentType;it[fileSizeBytes]=photo.fileSizeBytes;it[isMain]=main}
 }}
 override fun setMain(reportId:Long,uuid:String)=transaction{if(get(reportId,uuid)==null) null else {InspectionPhotoTable.update({InspectionPhotoTable.reportId eq reportId}){it[isMain]=false};InspectionPhotoTable.update({(InspectionPhotoTable.reportId eq reportId) and (InspectionPhotoTable.uuid eq uuid)}){it[isMain]=true};get(reportId,uuid)}}
 override fun delete(reportId:Long,uuid:String)=transaction{InspectionPhotoTable.deleteWhere{(InspectionPhotoTable.reportId eq reportId) and (InspectionPhotoTable.uuid eq uuid)}>0}
 private fun map(r:org.jetbrains.exposed.v1.core.ResultRow)=InspectionPhoto(r[InspectionPhotoTable.id].value,UUID.fromString(r[InspectionPhotoTable.uuid]),r[InspectionPhotoTable.reportId].value,r[InspectionPhotoTable.originalName],r[InspectionPhotoTable.storagePath],r[InspectionPhotoTable.thumbnailPath],r[InspectionPhotoTable.contentType],r[InspectionPhotoTable.fileSizeBytes],r[InspectionPhotoTable.isMain])
}
