import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService


def createCompany() {
    Map result = success()
    company = parameters.company
    userLogin = from("UserLogin").where("userLoginId", "admin").queryOne();
    GenericValue party = makeValue("Party")
        party.partyId = delegator.getNextSeqId("Party")
        party.partyTypeId = "PARTY_GROUP"
    party.create()
    GenericValue partyGroup = makeValue("PartyGroup")
        partyGroup.partyId = party.partyId
        partyGroup.groupName = company.name
    partyGroup.create()
    GenericValue partyClassification = makeValue("PartyClassification")
        partyClassification.partyId = party.partyId
        partyClassification.partyClassificationGroupId =
            company.classificationId
        partyClassification.fromDate = UtilDateTime.nowTimestamp()
    partyClassification.create()
    run service: 'createPartyEmailAddress',
            with: [partyId: party.partyId, emailAddress: company.email,
                    contactMechPurposeTypeId: 'PRIMARY_EMAIL',
                    userLogin: userLogin]
    GenericValue partyAcctgPreference = makeValue("PartyAcctgPreference")
        partyAcctgPreference.partyId = party.partyId
        partyAcctgPreference.baseCurrencyUomId = company.currencyId
    partyAcctgPreference.create()

    companyResult = run service: "getCompanies",
        with: [partyId: party.partyId]
    result.company = companyResult.company
    return result
}

def getCompanies() {
    Map result = success()

    if (!parameters.partyId) {
        companyList = from('CompanyPreferenceAndClassification')
            .where([partyClassificationGroupId:parameters.classificationId])
            .queryList()
    } else {
        companyList = from('CompanyPreferenceAndClassification')
            .where([partyId:parameters.partyId])
            .queryList()
    }
    if (!parameters.partyId) result.companies = [];
    companyList.each {
        resultEmail = runService('getPartyEmail', [partyId: it.partyId,
                                contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        company = [ // see model in https://github.com/growerp/growerp/blob/master/lib/models/company.dart
            partyId: it.partyId,
            name: it.organizationName,
            classificationId: it.partyClassificationGroupId,
            classificationDescr: it.description,
            email: resultEmail.emailAddress,
            currencyId: it.baseCurrencyUomId,
            image: null,
            employees: null
        ]
        if (!parameters.partyId) result.companies.add(company)
        else result.company = company
    }
    return result
}
