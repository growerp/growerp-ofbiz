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

def boolean isAdmin(userLogin) {
    if(userLogin?.userLoginId == 'system' || from("UserLoginSecurityGroup")
        .where([userLoginId: userLogin?.userLoginId,
            groupId: "GROWERP_M_ADMIN"]).queryList())
    return true
    else false
}

def getCatalog() { // from is company, to is employee
    Map result = success()
    result.categories = runService("getCategories100", [:]).categories
    result.products = runService("getProducts100", [:]).products
    return result
}

def getCategories() {
    Map result = success()
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    categoryList = []
    String imageSize = "GROWERP-SMALL" 
    if (!parameters.categoryId) {
        categoryList = from('CompanyCategory').where([ownerPartyId: companyPartyId]).queryList()
        result.categories = []
    }
    else {
        imageSize = "GROWERP-MEDIUM"
        categoryList = from('CompanyCategory')
            .where([ownerPartyId: companyPartyId,
                    productCategoryId: parameters.categoryId])
            .queryList()
    }
    categoryList.each {
        contents = from('ProductCategoryContent')
            .where([productCategoryId: it.productCategoryId, prodCatContentTypeId: imageSize])
            .queryList()
        String imageDataResource
        if (contents) {
            systemLogin = from("UserLogin").where([userLoginId: 'system']).queryOne()
            imageDataResource = runService('getContentAndDataResource',
                [contentId: contents[0].contentId, userLogin: systemLogin])
                    .resultData?.imageDataResource
        }
        category = [categoryId: it.productCategoryId,
                    categoryName: it.categoryName,
                    image: imageDataResource?.imageData?.encodeBase64().toString()]        
        if (parameters.categoryId) result.category = category
        else result.categories.add(category)    
    }
    return result
}
def getProducts() {
    Map result = success()
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    productList = []
    String imageSize = "GROWERP-SMALL" 
    if (!parameters.productId) {
        productList = from('CompanyProduct')
            .where([ownerPartyId: companyPartyId])
            .filterByDate("productFromDate", "productThruDate")
            .queryList()
        result.products = []
    } else {
        imageSize = "GROWERP-MEDIUM"
        productList = from('CompanyProduct')
            .where([ownerPartyId: companyPartyId,
                    productId: parameters.productId])
            .filterByDate("productFromDate", "productThruDate")
            .queryList()
    }
    productList.each {
        contents = from('ProductContent')
            .where([productId: it.productId, productContentTypeId: imageSize])
            .queryList()
        String imageDataResource
        if (contents) {
            systemLogin = from("UserLogin").where([userLoginId: 'system']).queryOne()
            imageDataResource = runService('getContentAndDataResource',
                [contentId: contents[0].contentId, userLogin: systemLogin])
                    .resultData?.imageDataResource
        }
        product = [ productId: it.productId,
                    productName: it.productName,
                    description: it.description,
                    price: it.price.toString(),
                    categoryId: it.productCategoryId,
                    image: imageDataResource?.imageData?.encodeBase64().toString()]        
        if (parameters.productId) result.product = product
        else result.products.add(product)    
    }
    return result    
}

def createProduct() {
    Map result = success()
    // only admin can add employees in his own company
    if (!isAdmin(parameters.userLogin)) { // only admin can
        return error("No access to user ${parameters.userLogin.partyId}")
    }
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    company = runService("getCompanies100", [companyPartyId: companyPartyId]).company
    productId = runService('createProduct',
        [ productName: parameters.product.productName,
          internalName: ' ',
          productTypeId: 'FINISHED_GOOD',
          description: parameters.product.description]).productId
    runService('createProductPrice',
        [ productId: productId,
          price: new BigDecimal(parameters.product.price),
          currencyUomId: company.currencyId,
          productStoreGroupId: '_NA_',
          productPricePurposeId: 'PURCHASE',
          productPriceTypeId: 'DEFAULT_PRICE'])
    check = from('CompanyCategory') // check if valid categoryId
            .where([productCategoryId: parameters.product.categoryId,
                        ownerPartyId: companyPartyId])
            .queryList()
    if (!check) 
        return error("CategoryId:${parameters.product.categoryId} not found")
    runService('addProductToCategory', [
            productId: productId, 
            productCategoryId: parameters.product.categoryId])
    if (parameters.base64) runService("createImages100",
        [ base64: parameters.base64,
          type: 'product',
          id: productId])
    result.product = runService('getProducts100',[productId: productId]).product
    return result
}
def updateProduct() {
    Map result = success()
    if (!isAdmin(parameters.userLogin)) { // only admin can
        return error("No access to user ${parameters.userLogin.partyId}")
    }
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId

    oldProduct = runService("getProducts100",[productId: parameters.product.productId]).product
    if (oldProduct?.productName != parameters.product.productName || 
            oldProduct.description != parameters.product.description) {
        runService('updateProduct',
            [   productId: parameters.product.productId,
                productName: parameters.product.productName])
    }

    if (oldProduct.price != parameters.product.price) { 
        pp = from("ProductPrice")
            .where([productId: parameters.product.productId]).queryList()
        pp[0].price = new BigDecimal(parameters.product.price)
        pp[0].store()
    }

    if (oldProduct.categoryId != parameters.product.categoryId) {
        members = from('ProductCategoryMember')
            .where([productId: parameters.product.productId,
                productcategoryId: parameters.product.categoryId])
            .filterByDate().queryList()
        members[0].thruDate = UtilDateTime.nowTimestamp()
        members[0].store()
        runService('addProductToCategory', [productId: productId, 
            productCategorId: parameters.product.categoryId])
    }
    if (parameters.product.image) runService("createImages100",
        [ base64: parameters.product.image,
          type: 'product',
          id: parameters.product.productId])
    result.product = runService('getProducts100',
        [productId: parameters.product.productId])?.product
    return result
}
def deleteProduct() {
    Map result = success()
    if (!isAdmin(parameters.userLogin)) { // only admin can
        return error("No access to user ${parameters.userLogin.partyId}")
    }
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    products = from('CompanyProduct')
        .where([productId: parameters.productId,
                ownerPartyId: companyPartyId])
        .filterByDate().queryList()
    if (!products)
        return error("Product; ${parameters.productId} not found")
    members = from('ProductCategoryMember')
        .where([productId: parameters.productId,
            productCategoryId: products[0].productCategoryId])
        .filterByDate().queryList()
    members[0].thruDate = UtilDateTime.nowTimestamp()
    members[0].store()
    result.productId = parameters.productId
    return result
}

def createCategory() {
    Map result = success()
    // only admin can add employees in his own company
    if (!isAdmin(parameters.userLogin)) { // only admin can
        return error("No access to user ${parameters.userLogin.partyId}")
    }
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    productCategoryId = runService('createProductCategory',
        [ categoryName: parameters.category.categoryName,
          productCategoryTypeId: 'CATALOG_CATEGORY']).productCategoryId
    catalogs = from('ProductStoreAndCatalog')
            .where([ownerPartyId: companyPartyId]).queryList()
    runService('addProductCategoryToProdCatalog', [
            productCategoryId: productCategoryId, 
            prodCatalogId: catalogs[0].prodCatalogId,
            prodCatalogCategoryTypeId: 'PCCT_BROWSE_ROOT'])
    if (parameters.base64) runService("createImages100",
        [ base64: parameters.base64,
          type: 'category',
          id: productCategoryId])
    result.category = runService('getCategories100',
        [categoryId: productCategoryId])?.category
    return result
}
def updateCategory() {
    Map result = success()
    if (!isAdmin(parameters.userLogin)) { // only admin can
        return error("No access to user ${parameters.userLogin.partyId}")
    }
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId

    oldCategory = runService('getCategories100',
        [categoryId: parameters.category.categoryId])?.category
    if (!oldCategory)
        return error("Category ${parameters.category.categoryName} not found!")

    if (oldCategory?.categoryName != parameters.category.categoryName) {
        prodCategory = from("ProductCategory")
            .where([productCategoryId: parameters.category.categoryId])
            .queryOne()
        prodCategory.categoryName = parameters.category.categoryName
        prodCategory.store()
    }

    if (parameters.category.image) runService("createImages100",
        [ base64: parameters.category.image,
          type: 'category',
          id: parameters.category.categoryId])
    result.category = runService('getCategories100',
        [categoryId: parameters.category.categoryId])?.category
    return result
}
def deleteCategory() {
    Map result = success()
    if (!isAdmin(parameters.userLogin)) { // only admin can
        return error("No access to user ${parameters.userLogin.partyId}")
    }
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    prodCatalogs = from('ProdCatalogCategory')
            .where([ownerPartyId: companyPartyId,
                    productCategoryId: parameters.categoryId]).queryList()
    if (!prodCatalogs) return error("CategoryId:${parameters.categoryId} not found")
    catalogCategory = from('ProdCatalogCategory')
        .where([productCategoryId: parameters.categoryId,
        prodCatalogId: prodCatalogs[0].prodCatalogId]).queryList()
    catalogCategory.thruDate = UtilDateTime.nowTimestamp()
    catalogCategory.store()
    runService('addProductCategoryToProdCatalog', [
            productCategoryId: parameters.categoryId, 
            prodCatalogId: prodCatalogs[0].prodCatalogId,
            prodCatalogCategoryTypeId: 'PCCT_BROWSE_ROOT'])
    result.categoryId = parameters.categoryId
    return result
}
