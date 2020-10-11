import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.common.image.ImageTransform

def getRelatedCompany() { // from is company, to is employee
    Map result = success()
    if (!parameters.userPartyId) // use login party when not provided
        parameters.userPartyId = parameters.userLogin.partyId
    rel = from('PartyRelationship')
        .where([ roleTypeIdFrom: 'INTERNAL_ORGANIZATIO',
                roleTypeIdTo: '_NA_',
                partyIdTo: parameters.userPartyId])
        .queryList()
    result.companyPartyId = rel[0]?.partyIdFrom
    return result
}
