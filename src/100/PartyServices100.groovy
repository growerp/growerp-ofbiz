/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.common.image.ImageTransform
import org.apache.ofbiz.common.scripting.ScriptHelperImpl
import java.awt.image.*
import java.awt.Image
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.Base64
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO
import org.apache.commons.lang.RandomStringUtils


def boolean isAdmin(userLogin) {
    if(userLogin?.userLoginId == 'system' || from("UserLoginSecurityGroup")
        .where([userLoginId: userLogin?.userLoginId,
            groupId: "GROWERP_M_ADMIN"]).queryList())
    return true
    else false
}

def getCompanies() { // get a single- or a list of companies
    Map result = success()
    List companyList
    String imageSize
    if (parameters.companyPartyId) {
        companyList = from('CompanyPreferenceAndClassification')
            .where([companyPartyId: parameters.companyPartyId])
            .queryList()
        imageSize = "GROWERP-MEDIUM"
    } else if (parameters.classificationId) {
        result.companies = []
        companyList = from('CompanyPreferenceAndClassification')
            .where([classificationId: parameters.classificationId])
            .queryList()
        imageSize = "GROWERP-SMALL"
    } else {
        result.companies = []
        companyList = from('CompanyPreferenceAndClassification')
            .queryList()
        imageSize = "GROWERP-SMALL"
    }
    companyList.each {
        email = runService('getPartyEmail',
            [   partyId: it.companyPartyId,
                contactMechPurposeTypeId: 'PRIMARY_EMAIL']).emailAddress
        users = runService('getUsers100', [companyPartyId: it.companyPartyId]).users
        contents = from('PartyContent')
            .where([partyId: it.companyPartyId, partyContentTypeId: imageSize])
            .queryList()
        Map imageDataResource
        if (contents) {
            systemLogin = from("UserLogin").where([userLoginId: 'system']).queryOne()
            imageDataResource = runService('getContentAndDataResource',
                [contentId: contents[0].contentId, userLogin: systemLogin])
                    .resultData?.imageDataResource
        }
        company = [ 
            partyId: it.companyPartyId,
            name: it.organizationName,
            classificationId: it.classificationId,
            classificationDescr: it.description,
            email: email,
            currencyId: it.baseCurrencyUomId,
            image: imageDataResource?.imageData?.encodeBase64().toString(),
            employees: users
        ]
        if (parameters.companyPartyId) result.company = company
        else result.companies.add(company)
    }
    return result
}

def checkCompany() {
    Map result = success()
    parties = from("CompanyPreferenceAndClassification")
        .where([companyPartyId: parameters.companyPartyId])
        .queryList()
    if(parties) result.ok = 'ok'
    return result
}

def updateCompany() { // can only update by admin own company
    Map result = success()
    if (isAdmin(parameters.userLogin) == false) return error("Not authorized!")
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    oldCompany = runService("getCompanies100",
        [companyPartyId: companyPartyId]).company
    if (parameters.company.name != oldCompany.name) {
        runService('updatePartyGroup', [partyId: companyPartyId,
            groupName: parameters.company.name])}
    cm = from("PartyContactMechPurpose")
        .where([partyId: companyPartyId,
                contactMechPurposeTypeId: 'PRIMARY_EMAIL']).queryList()
    if (parameters.company.email != oldCompany.email) {
        runService('createUpdatePartyEmailAddress',
            [ partyId: companyPartyId,
              contactMechId: cm[0].contactMechId,
              emailAddress: parameters.company.email,
              contactMechPurposeTypeId: 'PRIMARY_EMAIL'])}
    if (parameters.company.currencyId != oldCompany.currencyId) {
        accountingPref = select("partyId","currencyUomId")
            .from("PartyAccountingPreference")
            .where([partyId: companyPartyId]).queryOne()
        accountingPref.currencyUomId = parameters.company.currencyId
        accountingPref.store() }
    if (parameters.company.image) runService("createImages100",
        [ base64: parameters.company.image,
          type: 'company',
          id: parameters.company.partyId])
    result.company = runService("getCompanies100",
        [companyPartyId: companyPartyId]).company
    return result    
}

def registerUserAndCompany() {
    Map result = success()
    parameters.userLogin = from("UserLogin")
        .where([userLoginId: "system"]).queryOne();

    if (!parameters.companyPartyId) {
        companyPartyId = runService('createPartyGroup',
            [ groupName: parameters.companyName]).partyId
        runService('createPartyRole',
            [ partyId: companyPartyId,
              roleTypeId: 'INTERNAL_ORGANIZATIO'])
        runService('createPartyClassification',
            [ partyId: companyPartyId,
              partyClassificationGroupId: parameters.classificationId,
              fromDate: UtilDateTime.nowTimestamp()])
        runService('createPartyEmailAddress',
            [ partyId: companyPartyId,
              emailAddress: parameters.companyEmail,
              contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        runService('createPartyAcctgPreference',
            [ partyId: companyPartyId,
              baseCurrencyUomId: parameters.currencyId])
        productStoreId = runService('createProductStore',
            [ payToPartyId: companyPartyId,
              storeName: 'Store of ' + parameters.companyName]).productStoreId
        prodCatalogId = runService('createProdCatalog',
            [ catalogName: 'Catalog for company' + parameters.companyName,
              payToPartyId: companyPartyId]).prodCatalogId
        runService('createProductStoreCatalog',
            [ prodCatalogId: prodCatalogId,
              productStoreId: productStoreId,
              fromDate: UtilDateTime.nowTimestamp()])
    }
    user = [firstName: parameters.firstName,
            lastName: parameters.lastName,
            userGroupId: 'GROWERP_M_ADMIN',
            email: parameters.emailAddress,
            name: parameters.username,
            password: parameters.password]
    result.user = runService('createUser100',
        [ user: user, companyPartyId: companyPartyId])?.user
    result.company = runService("getCompanies100",
        [ companyPartyId: companyPartyId]).company
    return result
}

def getUsers() {
    Map result = success()
    List userList = []
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    //logInfo("====getUsers with userGroupId: ${parameters.userGroupId} companyPartyId: $companyPartyId")
    otherUserCompanyPartyId = runService("getRelatedCompany100", 
            [userPartyId: parameters.userPartyId]).companyPartyId
    //users own data or not have company(yet) suppliers, customers, leeds etc
    //logInfo("==1==logged in user: ${parameters.userLogin?.partyId} requested party: ${parameters.userPartyId}")

    if (parameters.userGroupId) {
        //logInfo("=====by usergroup over all companies===")
        // TODO: need some limitation by company here too.
        userList = from('PersonAndLoginGroup')
            .where([userGroupId: parameters.userGroupId])
            .queryList()
    } else if ((parameters?.userPartyId && 
            parameters?.userPartyId == parameters?.userLogin?.partyId)
                || !otherUserCompanyPartyId) {
        //logInfo("=====own data or customer====")
        userList = from('PersonAndLoginGroup')
            .where([userPartyId: parameters.userPartyId])
            .queryList()
    // other single users in the same company and admin
    } else if (parameters?.userPartyId && isAdmin(parameters.userLogin)) {
        //logInfo("====other user data by admin=========")
        userList = from('CompanyPersonAndLoginGroup')
            .where([userPartyId: parameters.userPartyId,
                        companyPartyId: companyPartyId])
            .queryList()
    // all users in the users company AND admin
    } else if (isAdmin(parameters.userLogin)) {// get all users of a company
        //logInfo("======all users of a company by admin=====")
        userList = from('CompanyPersonAndLoginGroup') // by company
            .where([companyPartyId: companyPartyId])
            .queryList()
    }
    //logInfo("====records found: ${userList?.size()}")
    String imageSize
    if (!parameters.userPartyId) {
        result.users = [];
        imageSize = "GROWERP-SMALL"
    } else {
        imageSize = "GROWERP-MEDIUM"
    }

    userList.each {
        contents = from('PartyContent')
            .where([partyId: it.userPartyId, partyContentTypeId: imageSize])
            .queryList()
        Map imageDataResource
        if (contents) {
            systemLogin = from("UserLogin").where([userLoginId: 'system']).queryOne()
            imageDataResource = runService('getContentAndDataResource',
                [contentId: contents[0].contentId, userLogin: systemLogin])
                    .resultData?.imageDataResource
        }
        resultEmail = runService('getPartyEmail',
            [ partyId: it.userPartyId,
              contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
        user = [ 
            partyId: it.userPartyId,
            firstName: it.firstName,
            lastName: it.lastName,
            email: resultEmail.emailAddress,
            name: it.userLoginId,
            userGroupId: it.groupId,
            groupDescription: it.groupDescription,
            image: imageDataResource?.imageData?.encodeBase64().toString()
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
    result.user = [:]
    logInfo("====incoming new user: ${parameters.user}")
    // only admin can add employees in his own company
    // companyPartyid = runService("getRelatedCompany100", [:]).companyPartyId
    String password = RandomStringUtils.randomAlphanumeric(6); 
    if (parameters.user.password) password = parameters.user.password
    userPartyId = runService('createPerson',
        [ firstName: parameters.user.firstName,
          lastName: parameters.user.lastName]).partyId
    runService('createPartyEmailAddress',
        [ partyId: userPartyId,
          emailAddress: parameters.user.email,
          contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
    loginResult = runService('createUserLogin',
        [ partyId: userPartyId,
          userLoginId: parameters.user.name,
          currentPassword: password,
          currentPasswordVerify: password])
    if (ServiceUtil.isError(loginResult)) return loginResult
    runService('createPartyRole',
        [ partyId: userPartyId,
          roleTypeId: 'OWNER'])
    runService('addUserLoginToSecurityGroup',
        [ userLoginId: parameters.user.name,
          groupId: parameters.user.userGroupId,
          fromDate: UtilDateTime.nowTimestamp()])
    runService('addUserLoginToSecurityGroup', // do not use OFBIZ security system
        [ userLoginId: parameters.user.name,
          groupId: 'SUPER',
          fromDate: UtilDateTime.nowTimestamp()])
    if (parameters.user.userGroupId in ['GROWERP_M_ADMIN', 'GROWERP_M_EMPLOYEE']) {
        if (!parameters.companyPartyId) {
            parameters.companyPartyId = 
                runService('getRelatedCompany100',[:]).companyPartyId
        }
        runService("createPartyRelationship",
            [ partyIdFrom: parameters.companyPartyId,
              roleTypeIdFrom: "INTERNAL_ORGANIZATIO",
              partyIdTo: userPartyId,
              roleTypeIdTo: "_NA_",
              fromDate: UtilDateTime.nowTimestamp()])
    }
    if (parameters.base64) runService("createImages100",
        [ base64: parameters.base64,
          type: 'user',
          id: userPartyId])
    runService("sendGenericNotificationEmail", [
        sendTo: user.email,
        sendFrom: UtilProperties.getPropertyValue('growerp', 'defaultFromEmailAddress'),
        subject: 'Welcome to the GrowERP system',
        templateName: 'component://growerp/template/email/forgotPassword.ftl',
        templateData: [ password: password ]
    ])   
    result.user = parameters.user
    result.user.partyId = userPartyId
    return result
}
def updateUser() {
    Map result = success()
    if (parameters.user.partyId != parameters.userLogin.partyId) { // own data
        companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
        loginCompanyPartyId = runService("getRelatedCompany100", 
            [userpartyid: parameters.user.partyId]).companyPartyId
        if (companyPartyId != loginCompanyPartyId) { // own company
            if (!isAdmin(parameters.userLogin)) { // only admin can
                return error("No access to user ${parameters.user.partyId}")
            }
        }
    }

    oldUser = runService("getUsers100",[userPartyId: parameters.user.partyId]).user
    if (oldUser?.lastName != parameters.user.lastName || 
            oldUser?.firstName != parameters.user.firstName) {
        runService('updatePerson',
            [   partyId: parameters.user.partyId,
                firstName: parameters.user.firstName,
                lastName: parameters.user.lastName])
    }

    if (oldUser.email != parameters.user.email) { 
        cm = from("PartyContactMechPurpose")
            .where([partyId: parameters.user.partyId,
                    contactMechPurposeTypeId: 'PRIMARY_EMAIL']).queryList()
        runService('createUpdatePartyEmailAddress',
            [   partyId: parameters.user.partyId,
                contactMechId: cm[0].contactMechId,
                emailAddress: parameters.user.email,
                contactMechPurposeTypeId: 'PRIMARY_EMAIL'])
    }
    if (oldUser.name != parameters.user.name) {
        loginResult = runService('updateUserLoginId',
            [ userLoginId: parameters.user.name])
        if (ServiceUtil.isError(loginResult)) return loginResult
    }
    if (oldUser.userGroupId != parameters.user.userGroupid
            || oldUser.name != parameters.user.name) {
        sec = from("UserLoginSecurityGroup")
                .where([userLoginId: oldUser.name,
                        groupId: oldUser.userGroupId]).queryList()
        runService('removeUserLoginToSecurityGroup',
            [ userLoginId: oldUser.name,
            fromDate: sec[0].fromDate,
            groupId: oldUser.userGroupId])
        runService('addUserLoginToSecurityGroup',
            [ userLoginId: parameters.user.name,
            groupId: parameters.user.userGroupId,
            fromDate: UtilDateTime.nowTimestamp()])
    }
    if (parameters.user.image) runService("createImages100",
        [ base64: parameters.user.image,
          type: 'user',
          id: parameters.user.partyId])
    result.user = runService('getUsers100',
        [userPartyId: parameters.user.partyId])?.user
    return result
}
def deleteUser() {
    Map result = success()
    if (!isAdmin(parameters.userLogin) && parameters.userPartyId != parameters.userLogin.partyId)
        return error("No access to user ${parameters.userPartyId}")
    parties = from("Party").where([partyId: parameters.userPartyId]).queryList()
    parties[0].statusId = "PARTY_DISABLED"
    parties[0].store()
    result.userPartyId = parameters.userPartyId
    return result
}

def updatePassword() {
    return runService("updatePassword", [
        currentPassword: parameters.oldPassword,
        newPassword: parameters.newPassword,
        newPasswordVerify: parameters.newPassword
    ])        
}

def resetPassword() {
    userLogin = from("UserLogin").where([userLoginId: 'system']).queryOne()
    String email;
    // try username
    login = from("UserLogin").where([userLoginId: parameters.username]).queryOne()
    if (login)
        email = runService("getPartyEmail", [partyId: login.partyId]).emailAddress
    else  { // try email
        partyId = runService("findPartyFromEmailAddress",
            [address: parameters.username, userLogin: userLogin]).partyId
        if (partyId) {
            email = parameters.username
        }
    }
    if (email) {
        newPassword = RandomStringUtils.randomAlphanumeric(6); 
        user = runService("updatePassword",
            [newPassword: newPassword, newPasswordVerify: newPassword,
                userLoginId: login.userLoginId, userLogin: userLogin]) 
        runService("sendGenericNotificationEmail", [
            sendTo: email,
            sendFrom: UtilProperties.getPropertyValue('growerp', 'defaultFromEmailAddress'),
            subject: 'Your new password from GrowERP',
            templateName: 'component://growerp/template/email/forgotPassword.ftl',
            templateData: [password: newPassword]
            ])
        logInfo("new password: $newPassword send to email: $email")
    } else logWarning("Reset password with not existing username/emailAddress")
    return success()
}

def checkToken() {
    Map result = success()
    result.ok = 'ok'
    return result
}

def getAuthenticate() {
    Map result = success()
    result.user = runService("getUsers100", // get single user info
        [userPartyId: parameters.userLogin.partyId])?.user
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    result.company = runService("getCompanies100", // get companyInfo
        [ companyPartyId: companyPartyId])?.company
    return result
}

def createImages() {
    Map result = success()
    byte[] inputBytes = Base64.getMimeDecoder().decode(parameters.base64);
    int fileSize = inputBytes.size()
    dataResourceId = runService("createDataResource",
        [dataResourceTypeId: 'IMAGE_OBJECT']).dataResourceId
    runService("createImageDataResource",
        [imageData: inputBytes, dataResourceId: dataResourceId])
    contentIdLarge = runService("createContent",
        [dataResourceId: dataResourceId]).contentId

    // byte[] to buffered image
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(inputBytes));

    // resize image
    def scale = 5000 / fileSize
    int newWidth = img.width * scale
    int newHeight = img.height * scale
    Image newImg = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    BufferedImage bufNewImg = ImageTransform.toBufferedImage(newImg,  img.getType());

    // bufferedImage back to byte[]
    baos = new ByteArrayOutputStream();
    ImageIO.write( bufNewImg, "png", baos );
    baos.flush();
    imageBytes = baos.toByteArray();
    baos.close();
    // save
    dataResourceId = runService("createDataResource",
        [dataResourceTypeId: 'IMAGE_OBJECT']).dataResourceId
    runService("createImageDataResource",
        [imageData: imageBytes, dataResourceId: dataResourceId])
    contentIdMedium = runService("createContent",
        [dataResourceId: dataResourceId]).contentId

    // scale image
    scale = 2000 / fileSize
    newWidth = img.width * scale
    newHeight = img.height * scale
    newImg = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    bufNewImg = ImageTransform.toBufferedImage(newImg, img.getType());

    // bufferedImage to byte[]
    baos = new ByteArrayOutputStream();
    ImageIO.write( bufNewImg, "png", baos );
    baos.flush();
    imageBytes = baos.toByteArray();
    baos.close();
    //save
    dataResourceId = runService("createDataResource",
        [dataResourceTypeId: 'IMAGE_OBJECT']).dataResourceId
    runService("createImageDataResource",
        [imageData: imageBytes, dataResourceId: dataResourceId])
    contentIdSmall = runService("createContent",
        [dataResourceId: dataResourceId]).contentId

    if (parameters.type in ['user', 'company']) {
        runService("createPartyContent",
            [ partyId: parameters.id,
              contentId: contentIdLarge,
              partyContentTypeId: 'GROWERP-LARGE'])
        runService("createPartyContent",
            [ partyId: parameters.id,
              contentId: contentIdMedium,
              partyContentTypeId: 'GROWERP-MEDIUM'])
        runService("createPartyContent",
            [ partyId: parameters.id,
              contentId: contentIdSmall,
              partyContentTypeId: 'GROWERP-SMALL'])
    }
    return result
}



