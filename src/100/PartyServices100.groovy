import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

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
        resultUsers = runService('getUsers100', [companyPartyId: it.partyId])
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
    logInfo("=Get companies:==${result.companies? result.companies.size():''} ${result.company?1:''} found")
    return result
}

def registerUserAndCompany() {
    Map result = success()
    parameters.userLogin = from("UserLogin")
        .where("userLoginId", "system").queryOne();

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
    loginResult = run service: 'createUserLogin',
        with: [ partyId: personResult.partyId,
                userLoginId: parameters.username,
                currentPassword: parameters.password,
                currentPasswordVerify: parameters.passwordVerify]
    if (ServiceUtil.isError(loginResult)) return loginResult
    run service: 'addUserLoginToSecurityGroup',
        with: [ userLoginId: parameters.username,
                groupId: parameters.userGroupId,
                fromDate: UtilDateTime.nowTimestamp()]
    run service: 'createPartyRelationship',
        with: [ partyIdTo: companyResult.partyId,
                roleTypeIdTo: "INTERNAL_ORGANIZATIO",
                partyIdFrom: personResult.partyId,
                fromDate: UtilDateTime.nowTimestamp()]
    resultCompany = run service: "getCompanies100",
        with: [partyId: companyResult.partyId]
    result.company = resultCompany.company
    resultUser = run service: "getUsers100",
        with: [userPartyId: personResult.partyId]
    result.user = resultUser.user
    return result
}

def getUsers() {
    Map result = success()

    if (parameters.companyPartyId) {
        userList = from('CompanyPersonAndLoginGroup') // by company
            .where([companyPartyId: parameters.companyPartyId])
            .queryList()
    } else if (parameters.userPartyId){ // specific user
        userList = from('PersonAndLoginGroup')
            .where([personPartyId: parameters.userPartyId])
            .queryList()
    } 
    if (!parameters.userPartyId) result.users = [];
    userList.each {
        resultEmail = runService('getPartyEmail', [partyId: it.personPartyId,
                                contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        // see model in https://github.com/growerp/growerp/blob/master/lib/models/user.dart
        user = [ 
            partyId: it.personPartyId,
            firstName: it.firstName,
            lastName: it.lastName,
            email: resultEmail.emailAddress,
            name: it.userLoginId,
            userGroupId: it.groupId,
            groupDescription: it.groupDescription,
            image: null,
        ]
        if (!parameters.userPartyId) result.users.add(user)
        else result.user = user
    }
    return result
}

def getAuthenticate() {
    Map result = success()
    resultUser = run service: "getUsers100",
        with: [userPartyId: parameters.userLogin.partyId]
    result.user = resultUser.user

    resultRelCompany = run service: 'getRelatedCompany100'

    resultCompany = run service: "getCompanies100",
        with: [partyId: resultRelCompany.companyPartyId]
    result.company = resultCompany.company
    return result
}
