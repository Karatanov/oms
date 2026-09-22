package oms.umitaf.dto
import kotlinx.serialization.Serializable
@Serializable data class InspectionPhotoResponse(val uuid:String,val fileName:String,val contentType:String,val fileSizeBytes:Long,val isMain:Boolean,val downloadUrl:String,val thumbnailUrl:String)
