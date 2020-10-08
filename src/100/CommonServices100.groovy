import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.common.image.ImageTransform
import java.awt.image.*
import java.awt.Image

def getRelatedCompany() { // from is company, to is employee
    Map result = success()
    relations = from('PartyRelationship')
            .where([partyIdTo: parameters.userLogin.partyId])
            .queryList()
    if (!relations) logError("No related company found for partyId: ${userLogin.partyId}")
    result.companyPartyId = relations[0].partyIdFrom
    return result 
}
