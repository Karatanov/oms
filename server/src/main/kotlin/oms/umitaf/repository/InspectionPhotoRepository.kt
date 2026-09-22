package oms.umitaf.repository
import oms.umitaf.domain.InspectionPhoto
interface InspectionPhotoRepository { fun list(reportId:Long):List<InspectionPhoto>; fun get(reportId:Long,uuid:String):InspectionPhoto?; fun create(photo:InspectionPhoto); fun setMain(reportId:Long,uuid:String):InspectionPhoto?; fun delete(reportId:Long,uuid:String):Boolean }
