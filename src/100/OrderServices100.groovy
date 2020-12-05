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

def createOrder() { // from is company, to is employee
    Map result = success()
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    company = runService("getCompanies100", [companyPartyId: companyPartyId]).company

    orderId = runService('createOrderHeader',
            [currencyUomId: company.currencyId,
             orderTypeId: 'SALES_ORDER']).orderId
    runService('ensurePartyRole',
            [partyId: order.customerPartyId, roleTypeId: 'BILL_TO_CUSTOMER'])
    runService('addOrderRole', [
            orderId: orderId,
            roleTypeId: 'BILL_TO_CUSTOMER',
            partyId: order.customerPartyId ])
    runService('ensurePartyRole',
            [partyId: company.partyId, roleTypeId: 'BILL_FROM_VENDOR'])
    runService('addOrderRole', [
            orderId: orderId,
            roleTypeId: 'BILL_FROM_VENDOR',
            partyId: company.partyId ])
    BigDecimal grandTotal = BigDecimal.ZERO
    order.orderItems.eachWithIndex { item,index ->
        grandTotal = grandTotal.add(
            new BigDecimal(item.quantity).multiply(new BigDecimal(item.price)))
        GenericValue orderItem = makeValue('OrderItem')
        orderItem.orderId = orderId
        orderItem.orderItemSeqId = (index+1).toString().padLeft(3,'0')
        orderItem.productId = item.productId
        orderItem.itemDescription = item.description
        orderItem.quantity = item.quantity
        orderItem.unitPrice = item.price
        orderItem.create()
    }
    order = from('OrderHeader').where([orderId: orderId]).queryOne()
    order.grandTotal = grandTotal;
    order.store()
    result.order = runService('getOrders100', [orderId: orderId]).order
    return result
}

def getOrders() {
    Map result = success()
    companyPartyId = runService("getRelatedCompany100", [:]).companyPartyId
    orders = []
    if (parameters.orderId && parameters.orderId != null) {
        result.order = [:]
        orders = from('OrderHeaderAndRoles')
                .where([roleTypeId: 'BILL_FROM_VENDOR',
                        orderId: parameters.orderId,
                        partyId: companyPartyId]).queryList()
    } else {
        result.orders = []
        orders = from('OrderHeaderAndRoles')
                .where([roleTypeId: 'BILL_FROM_VENDOR',
                        partyId: companyPartyId]).queryList()
    }
    orders.each{ order ->
        orderItems = from('OrderItem')
                    .where([orderId: order.orderId]).queryList()
        items = []
        orderItems.each{ item ->
            item = [
                orderItemSeqId: item.orderItemSeqId,
                productId: item.productId,
                quantity: item.quantity.toString(),
                price: item.unitPrice.toString(),
                description: item.itemDescription,
                fromDate: null, //TODO: should come from related workeffort
                thruDate: null // or field to be added to orderItem but need location(facility) too
            ]
            items.add(item)
        }
        orderRoles = from('OrderRole')
            .where([roleTypeId: 'BILL_TO_CUSTOMER',
                    orderId: order.orderId]).queryList()
        user = runService('getUsers100', [userPartyId: orderRoles[0].partyId]).user
        String orderStatusId = '???';
        if (order.statusId == 'ORDER_CREATED') orderStatusId = 'OrderOpen';
        orderOut = [
            orderId: order.orderId,
            orderStatusId: orderStatusId,
            placedDate: order.orderDate.toString().substring(0,19) + 'Z',
            customerPartyId: orderRoles[0].partyId,
            firstName: user?.firstName,
            lastName: user?.lastName,
            email: user?.email,
            grandTotal: order.grandTotal.toString(),
            orderItems: items
        ]
        if (!parameters.orderId) result.orders.add(orderOut)
        else result.order = orderOut
    }
    return result
}
