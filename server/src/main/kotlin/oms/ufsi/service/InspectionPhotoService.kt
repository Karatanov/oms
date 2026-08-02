package oms.ufsi.service
import oms.ufsi.domain.InspectionPhoto
import oms.ufsi.repository.InspectionPhotoRepository
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
class InspectionPhotoService(private val repository:InspectionPhotoRepository){
 fun list(reportId:Long)=repository.list(reportId); fun get(reportId:Long,uuid:String)=repository.get(reportId,uuid); fun setMain(reportId:Long,uuid:String)=repository.setMain(reportId,uuid)
 fun upload(reportId:Long,name:String,contentType:String?,bytes:ByteArray):InspectionPhoto{require(bytes.size in 1..10_000_000){"Photo size must not exceed 10 MB."};val ext=name.substringAfterLast('.',"").lowercase();require(ext in setOf("jpg","jpeg","png")){"Allowed photo formats: JPG, PNG."};val image=ImageIO.read(ByteArrayInputStream(bytes))?:throw IllegalArgumentException("Invalid image file.");val uuid=UUID.randomUUID();val dir=Path.of("uploads","inspection-photos").toAbsolutePath().normalize();Files.createDirectories(dir);val original=dir.resolve("$uuid.$ext");val thumb=dir.resolve("$uuid-thumb.jpg");try{Files.write(original,bytes);val ratio=minOf(1.0,320.0/image.width);val width=(image.width*ratio).toInt();val height=(image.height*ratio).toInt();val target=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);val g=target.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,width,height,null)}finally{g.dispose()};ImageIO.write(target,"jpg",thumb.toFile());val photo=InspectionPhoto(0,uuid,reportId,name,original.toString(),thumb.toString(),contentType?:"image/$ext",bytes.size.toLong(),repository.list(reportId).isEmpty());repository.create(photo);return photo}catch(e:Exception){Files.deleteIfExists(original);Files.deleteIfExists(thumb);throw e}}
 fun delete(reportId:Long,uuid:String):Boolean{val p=get(reportId,uuid)?:return false;val removed=repository.delete(reportId,uuid);if(removed){Files.deleteIfExists(Path.of(p.storagePath));Files.deleteIfExists(Path.of(p.thumbnailPath))};return removed}
}
