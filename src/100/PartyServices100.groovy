import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService


def registerUserAndCompany() {
    Map result = success()
    logInfo("==register=======params: ${parameters}")
    parameters.userLogin = from("UserLogin").where("userLoginId", "system").queryOne();

    if (!parameters.companyPartyId) {
        companyResult = run service: 'createPartyGroup',
            with: [ groupName: parameters.companyName]
        run service: 'createPartyRole',
            with: [ partyId: companyResult.partyId,
                    roleTypeId: 'INTERNAL_ORGANIZATIO']
        run service: 'createPartyClassification',
            with: [ partyId: companyResult.partyId,
                    partyClassificationGroupId: parameters.classificationId,
                    fromDate: UtilDateTime.nowTimestamp()]
        run service: 'createPartyEmailAddress',
            with: [ partyId: companyResult.partyId,
                    emailAddress: parameters.companyEmail,
                    contactMechPurposeTypeId: 'PRIMARY_EMAIL']
        run service: 'createPartyAcctgPreference',
            with: [ partyId: companyResult.partyId, 
                    baseCurrencyUomId: parameters.currencyId]
        psResult = run service: 'createProductStore',
            with: [ payToPartyId: companyResult.partyId,
                    storeName: 'Store of ' + parameters.companyName]
        pcResult = run service: 'createProdCatalog',
            with: [ catalogName: 'Catalog for company' + parameters.companyName,
                    payToPartyId: companyResult.partyId]
        run service: 'createProductStoreCatalog',
            with: [ prodCatalogId: pcResult.prodCatalogId,
                    productStoreId: psResult.productStoreId, 
                    fromDate: UtilDateTime.nowTimestamp()]
    }
    personResult = run service: 'createPerson',
        with: [ firstName: parameters.firstName,
                lastName: parameters.lastName]
    run service: 'createPartyEmailAddress',
        with: [ partyId: personResult.partyId,
                emailAddress: parameters.emailAddress,
                contactMechPurposeTypeId: 'PRIMARY_EMAIL']
    run service: 'createUserLogin',
        with: [ partyId: personResult.partyId,
                userLoginId: parameters.emailAddress,
                currentPassword: 'qqqqqq9!',
                currentPasswordVerify: 'qqqqqq9!']
    run service: 'addUserLoginToSecurityGroup',
        with: [ userLoginId: parameters.emailAddress,
                groupId: parameters.userGroupId,
                fromDate: UtilDateTime.nowTimestamp()]
    run service: 'createPartyRelationship',
        with: [ partyIdFrom: personResult.partyId,
                partyIdTo: companyResult.partyId,
                roleTypeIdTo: "INTERNAL_ORGANIZATIO",
                fromDate: UtilDateTime.nowTimestamp()]
    logInfo("======getting user and company")
    resultCompany = run service: "getCompanies",
        with: [partyId: companyResult.partyId]
    result.company = resultCompany.company
    resultUser = run service: "getUsers",
        with: [userPartyId: personResult.partyId]
    result.user = resultUser.user
    return result
}

def getCompanies() {
    logInfo("==get comp======params: ${parameters}")
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
        resultUsers = runService('getUsers', [companyPartyId: it.partyId])
        company = [ // see model in https://github.com/growerp/growerp/blob/master/lib/models/company.dart
            partyId: it.partyId,
            name: it.organizationName,
            classificationId: it.partyClassificationGroupId,
            classificationDescr: it.description,
            email: resultEmail.emailAddress,
            currencyId: it.baseCurrencyUomId,
            image: null,
            employees: resultUsers.users
        ]
        if (!parameters.partyId) result.companies.add(company)
        else result.company = company
    }
    return result
}
def getUsers() {
    logInfo("==get user=======params: ${parameters}")
    Map result = success()

    if (parameters.companyPartyId) {
        userList = from('CompanyPersonAndLoginGroup') // by company
            .where([companyPartyId: parameters.companyPartyId])
            .queryList()
    } else if (parameters.userPartyId){ // specific user
        userList = from('PersonAndLoginGroup')
            .where([userPartyId: parameters.userPartyId])
            .queryList()
    } 
    if (!parameters.userPartyId) result.users = [];
    userList.each {
        resultEmail = runService('getPartyEmail', [partyId: it.userPartyId,
                                contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        // see model in https://github.com/growerp/growerp/blob/master/lib/models/user.dart
        user = [ 
            partyId: it.userPartyId,
            firstName: it.firstName,
            lastName: it.lastName,
            email: resultEmail.emailAddress,
            userGroupId: it.groupId,
            image: null,
        ]
        if (!parameters.userPartyId) result.users.add(user)
        else result.user = user
    }
    return result
}
