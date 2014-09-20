/**
 * Copyright (c) 2012, Mayocat <hello@mayocat.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mayocat.shop.marketplace.web

import com.google.common.base.Optional
import com.google.common.base.Strings
import com.google.common.collect.Maps
import groovy.transform.CompileStatic
import org.mayocat.attachment.model.Attachment
import org.mayocat.attachment.store.AttachmentStore
import org.mayocat.configuration.PlatformSettings
import org.mayocat.configuration.general.GeneralSettings
import org.mayocat.context.WebContext
import org.mayocat.image.model.Image
import org.mayocat.image.model.Thumbnail
import org.mayocat.image.store.ThumbnailStore
import org.mayocat.rest.Resource
import org.mayocat.rest.Reference
import org.mayocat.shop.cart.Cart
import org.mayocat.shop.cart.CartItem
import org.mayocat.shop.cart.CartManager
import org.mayocat.shop.cart.InvalidCartOperationException
import org.mayocat.shop.cart.web.object.CartWebObject
import org.mayocat.shop.catalog.model.Product
import org.mayocat.shop.catalog.model.Purchasable
import org.mayocat.shop.catalog.store.ProductStore
import org.mayocat.shop.front.views.WebView
import org.mayocat.shop.marketplace.model.EntityAndTenant
import org.mayocat.shop.marketplace.store.MarketplaceProductStore
import org.mayocat.shop.marketplace.web.object.MarketplaceCartWebObject
import org.mayocat.shop.shipping.ShippingOption
import org.mayocat.shop.shipping.ShippingService
import org.xwiki.component.annotation.Component

import javax.inject.Inject
import javax.inject.Provider
import javax.ws.rs.Consumes
import javax.ws.rs.DefaultValue
import javax.ws.rs.FormParam
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response

/**
 * @version $Id$
 */
@Component("/marketplace/cart")
@Path("/marketplace/cart")
@Produces([MediaType.TEXT_HTML, MediaType.APPLICATION_JSON])
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@CompileStatic
class MarketplaceCartWebView implements Resource
{
    @Inject
    Provider<MarketplaceProductStore> marketplaceProductStore

    @Inject
    Provider<ProductStore> productStore

    @Inject
    CartManager cartManager

    @Inject
    Provider<AttachmentStore> attachmentStore

    @Inject
    Provider<ThumbnailStore> thumbnailStore

    @Inject
    ShippingService shippingService

    @Inject
    GeneralSettings generalSettings

    @Inject
    PlatformSettings platformSettings

    @Inject
    WebContext context

    @POST
    @Path("add")
    Response addToCart(@FormParam("product") Reference productReference, @FormParam("variant") String variantSlug,
            @FormParam("quantity") @DefaultValue("1") Long quantity)
            throws URISyntaxException
    {
        if (!productReference || Strings.isNullOrEmpty(productReference.tenantSlug) ||
                Strings.isNullOrEmpty(productReference.entitySlug))
        {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        EntityAndTenant<Product> productAndTenant = marketplaceProductStore.get().
                findBySlugAndTenant(productReference.entitySlug, productReference.tenantSlug)

        def product = productAndTenant.entity

        if (!Strings.isNullOrEmpty(variantSlug)) {
            Product variant = productStore.get().findVariant(product, variantSlug)
            if (variant == null) {
                return Response.status(Response.Status.BAD_REQUEST).build()
            }
            variant.setParent(product)
            product = variant
        }

        if (product == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Product not found").build()
        }

        cartManager.addItem(product, quantity)

        return Response.seeOther(new URI("/cart")).build()
    }

    @POST
    @Path("remove")
    Response removeFromCart(@FormParam("product") Reference productReference)
            throws URISyntaxException
    {
        if (!productReference || Strings.isNullOrEmpty(productReference.tenantSlug) ||
                Strings.isNullOrEmpty(productReference.entitySlug))
        {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        EntityAndTenant<Product> productAndTenant = marketplaceProductStore.get().
                findBySlugAndTenant(productReference.entitySlug, productReference.tenantSlug)

        def product = productAndTenant.entity

        if (product == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Product not found").build()
        }

        cartManager.removeItem(product)

        return Response.seeOther(new URI("/cart")).build()
    }

    @POST
    @Path("update")
    Response updateCart(MultivaluedMap<String, String> queryParams) throws URISyntaxException
    {
        boolean isRemoveItemRequest = false

        for (String key : queryParams.keySet()) {
            if (key.startsWith("remove_")) {
                // Handle "remove product" request
                isRemoveItemRequest = true
                try {
                    Integer index = Integer.valueOf(key.substring("remove_".length()))
                    cartManager.removeItem(index)
                } catch (NumberFormatException | InvalidCartOperationException e) {
                    return Response.status(Response.Status.BAD_REQUEST).build()
                }
            }
        }

        if (!isRemoveItemRequest) {
            // Handle update request
            for (String key : queryParams.keySet()) {
                if (key.startsWith("quantity_")) {
                    try {
                        Long quantity = Long.valueOf(queryParams.getFirst(key))
                        Integer index = Integer.valueOf(key.substring("quantity_".length()))

                        if (quantity <= 0) {
                            cartManager.removeItem(index)
                        } else {
                            cartManager.setQuantity(index, quantity)
                        }
                    } catch (NumberFormatException | InvalidCartOperationException e) {
                        return Response.status(Response.Status.BAD_REQUEST).build()
                    }
                }
            }
        }

        if (queryParams.getFirst("shipping_option") != null) {
            UUID carrierId = UUID.fromString(queryParams.getFirst("shipping_option"))

            Map<Purchasable, Long> items = Maps.newHashMap()

            ShippingOption option = shippingService.getOption(carrierId, items)
            cartManager.setSelectedShippingOption(option)
        }

        return Response.seeOther(new URI("/cart")).build()
    }

    @GET
    WebView getCart()
    {
        Cart cart = cartManager.getCart()
        if (!cart.isEmpty() && shippingService.isShippingEnabled() && cart.selectedShippingOption() == null) {
            Map<Purchasable, Long> items = Maps.newHashMap()
            List<ShippingOption> options = shippingService.getOptions(items)
            if (!options.isEmpty()) {
                cartManager.setSelectedShippingOption(options.get(0))
                cart = cartManager.getCart()
            }
        }

        Map<String, Object> data = new HashMap<String, Object>()

        final Locale locale = generalSettings.locales.mainLocale.value

        List<UUID> featuredImageIds = cart.items()
                .collect({ CartItem item -> item.item().featuredImageId })
                .findAll({ UUID id -> id != null }) as List<UUID>

        List<Attachment> attachments =
                featuredImageIds.isEmpty() ? [] as List<Attachment> : attachmentStore.get().findByIds(featuredImageIds)
        List<Thumbnail> thumbnails =
                featuredImageIds.isEmpty() ? [] as List<Thumbnail> :
                        thumbnailStore.get().findAllForIds(featuredImageIds)

        List<Image> images = attachments.collect({ Attachment attachment ->
            List<Thumbnail> thumbs = thumbnails.
                    findAll({ Thumbnail thumbnail -> thumbnail.attachmentId == attachment.id }) as List<Thumbnail>
            new Image(attachment, thumbs)
        })

        MarketplaceCartWebObject cartWebObject = new MarketplaceCartWebObject()
        cartWebObject.withCart(shippingService, cart, locale, images, platformSettings, Optional.absent())

        data.put("cart", cartWebObject)

        return new WebView().template("cart.html").data(data)
    }
}