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

def getCompanies() {
    Map result = success()

    if (!parameters.companyPartyId) {
        companyList = from('CompanyPreferenceAndClassification')
            .where([partyClassificationGroupId:parameters.classificationId])
            .queryList()
    } else {
        companyList = from('CompanyPreferenceAndClassification')
            .where([partyId:parameters.companyPartyId])
            .queryList()
    }
    if (!parameters.companyPartyId)
        result.companies = [];

    companyList.each {
        resultEmail = runService('getPartyEmail',
            [partyId: it.partyId,
             contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        resultUsers = runService('getUsers100', [companyPartyId: it.partyId])
        resultContent = from('PartyContent')
            .where([partyId: it.partyId, partyContentTypeId: "GROWERP-SMALL"])
            .queryOne()
        Map resultData
        if (resultContent)
            resultData = runService('getContentAndDataResource',
                [contentId: resultContent.contentId])
        company = [ // see model in https://github.com/growerp/growerp/blob/master/lib/models/company.dart
            partyId: it.partyId,
            name: it.organizationName,
            classificationId: it.partyClassificationGroupId,
            classificationDescr: it.description,
            email: resultEmail.emailAddress,
            currencyId: it.baseCurrencyUomId,
            image: resultData? resultData.resultData.dataResource.imageDataResource.imageData : null,
            employees: resultUsers.users
        ]
        if (!parameters.companyPartyId) result.companies.add(company)
        else result.company = company
    }
    return result
}

def checkCompany() {
    
}

def updateCompany() {
    
}

def registerUserAndCompany() {
    Map result = success()
    parameters.userLogin = from("UserLogin")
        .where([userLoginId: "system"]).queryOne();

    if (!parameters.companyPartyId) {
        companyResult = runService('createPartyGroup',
            [ groupName: parameters.companyName])
        runService('createPartyRole',
            [ partyId: companyResult.partyId,
              roleTypeId: 'INTERNAL_ORGANIZATIO'])
        runService('createPartyClassification',
            [ partyId: companyResult.partyId,
              partyClassificationGroupId: parameters.classificationId,
              fromDate: UtilDateTime.nowTimestamp()])
        runService('createPartyEmailAddress',
            [ partyId: companyResult.partyId,
              emailAddress: parameters.companyEmail,
              contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        runService('createPartyAcctgPreference',
            [ partyId: companyResult.partyId,
              baseCurrencyUomId: parameters.currencyId])
        psResult = runService('createProductStore',
            [ payToPartyId: companyResult.partyId,
              storeName: 'Store of ' + parameters.companyName])
        pcResult = runService('createProdCatalog',
            [ catalogName: 'Catalog for company' + parameters.companyName,
              payToPartyId: companyResult.partyId])
        runService('createProductStoreCatalog',
            [ prodCatalogId: pcResult.prodCatalogId,
              productStoreId: psResult.productStoreId,
              fromDate: UtilDateTime.nowTimestamp()])
    }
    user = [firstName: parameters.firstName,
            lastName: parameters.lastName,
            userGroupId: 'GROWERP_M_ADMIN',
            email: parameters.emailAddress,
            username: parameters.username
            ]
    resultUser = runService('createUser100',
        [ user: user,
          companyPartyId: companyResult.partyId])
    result.user = resultUser.user
    resultCompany = runService("getCompanies100",
        [ partyId: companyResult.partyId])
    result.company = resultCompany.company

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
        resultEmail = runService('getPartyEmail',
            [ partyId: it.personPartyId,
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

def loadDefaultData() {
    
}
def createUser() {
    Map result = success()
    String password = 'qqqqqq9!'
    personResult = runService('createPerson',
        [ firstName: parameters.user.firstName,
          lastName: parameters.user.lastName])
    runService('createPartyEmailAddress',
        [ partyId: personResult.partyId,
          emailAddress: parameters.user.email,
          contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
    loginResult = runService('createUserLogin',
        [ partyId: personResult.partyId,
          userLoginId: parameters.user.username,
          currentPassword: password,
          currentPasswordVerify: password])
    if (ServiceUtil.isError(loginResult)) return loginResult
    runService('addUserLoginToSecurityGroup',
        [ userLoginId: parameters.user.username,
          groupId: parameters.user.userGroupId,
          fromDate: UtilDateTime.nowTimestamp()])
    if (parameters.user.userGroupId in ['GROWERP_M_ADMIN', 'GROWERP_M_EMPLOYEE']) {
        if (!parameters.companyPartyId) {
            result = runService('getRelatedCompany100'
                [parameters.companyPartyId = result.companyPartyid])
        }
        runService("createPartyRelationship",
            [ partyIdFrom: parameters.companyPartyId,
              roleTypeIdFrom: "INTERNAL_ORGANIZATIO",
              partyIdTo: personResult.partyId,
              roleTypeIdTo: "_NA_",
              fromDate: UtilDateTime.nowTimestamp()])
    }
    if (parameters.base64) runService("createImages100",
        [ base64: parameters.base64,
          type: 'user',
          id: personResult.partyId])

    resultUser = runService("getUsers100",
        [userPartyId: personResult.partyId])
    result.user = resultUser.user
    return result
}
def updateUser() {
    Map result = success()

    if (parameters.base64) runService("createImages",
        [ base64: parameters.base64,
          type: 'user',
          id: parameters.user.partyId])

}
def deleteUser() {
    Map result = success()
    
}

def updatePassword() {
    Map result = success()
        
}

def resetPassword() {
    Map result = success()
        
}

def getAuthenticate() {
    Map result = success()
    resultUser = runService("getUsers100", // get single user info
        [userPartyId: parameters.userLogin.partyId])
    result.user = resultUser.user
    resultRelCompany = runService("getRelatedCompany100", [:])
    resultCompany = runService("getCompanies100", // get companyInfo
        [ companyPartyId: resultRelCompany.companyPartyId])
    result.company = resultCompany.company
    return result
}

def createImages() {
    Map result = success()
    byte[] imageBytes = Base64.decodeBase64(parameters.base64);
    int fileSize = imageBytes.size()
    drResult = runService("createImageDataResource",
        [imagedata: imageBytes])
    contentResultLarge = runService("createContent",
        [dataResourceId: drResult.dataResourceId])

    // byte[] to buffered image
    fileStreamLarge = imageBytes.openStream()
    BufferedImage img = ImageIO.read(fileStreamLarge);

    inst scaleFator = 5000 / fileSize
    // resize image
    Image newImg = bufImg.getScaledInstance((int) (img.getWidth() * scaleFactor),
        (int) (img.getHeight() * scaleFactor), Image.SCALE_SMOOTH);
    BufferedImage bufNewImg = ImageTransform.toBufferedImage(newImg, bufImgType);
    // bufferedImage to byte[]
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write( bufNewImg, "jpg", baos );
    baos.flush();
    imageBytes = baos.toByteArray();
    baos.close();
    drResult = run service: "createImageDataResource",
        with: [imagedata: imageBytes]
    contentResultMedium = run service: "createContent",
        with: [dataResourceId: drResult.dataResourceId]

    int scaleFator = 2000 / fileSize
    // resize image
    newImg = bufImg.getScaledInstance((int) (img.getWidth() * scaleFactor),
        (int) (img.getHeight() * scaleFactor), Image.SCALE_SMOOTH);
    bufNewImg = ImageTransform.toBufferedImage(newImg, bufImgType);
    // bufferedImage to byte[]
    baos = new ByteArrayOutputStream();
    ImageIO.write( bufNewImg, "jpg", baos );
    baos.flush();
    imageBytes = baos.toByteArray();
    baos.close();
    drResult = runService("createImageDataResource",
        [imagedata: imageBytes])
    contentResultSmall = runService("createContent",
        [dataResourceId: drResult.dataResourceId])

    if (parameters.type in ['user', 'company']) {
        result = runService("createPartyContent",
            [ partyId: parameters.id,
              contentId: contentResult.contentIdLarge,
              partyContentTypeId: 'GROWERP-LARGE'])
        result = runService("createPartyContent",
            [ partyId: parameters.id,
              contentId: contentResult.contentIdMedium,
              partyContentTypeId: 'GROWERP-MEDIUM'])
        result = runService("createPartyContent",
            [ partyId: parameters.id,
              contentId: contentResult.contentIdSmall,
              partyContentTypeId: 'GROWERP-SMALL'])
    }
    return result
}
