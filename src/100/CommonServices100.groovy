import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

def getRelatedCompany() {
    Map result = success()
    relations = from('PartyRelationship')
            .where([partyIdFrom:parameters.userLogin.partyId])
            .queryList()
    result.companyPartyId = relations[0].partyIdTo
    return result 
}
