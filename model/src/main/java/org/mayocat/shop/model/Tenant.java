package org.mayocat.shop.model;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class Tenant extends Entity
{
    public Tenant(@Valid String handle)
    {
        setHandle(handle);
    }
    
    private Long id;

    @NotNull
    @Pattern (message="Only word characters or hyphens", regexp="\\w[\\w-]*\\w")
    private String handle;
    
    List<String> aliases;

    ///////////////////////////////////////////////////
    
    public Long getId()
    {
        return id;
    }
    
    public String getHandle()
    {
        return handle;
    }

    public void setHandle(String handle)
    {
        this.handle = handle;
    }

    public List<String> getAliases()
    {
        return aliases;
    }

    public void setAliases(List<String> aliases)
    {
        this.aliases = aliases;
    }

    /*
    public void addToProducts(Product p)
    {
        if (this.products == null) {
            this.products = new ArrayList<Product>();
        }
        this.products.add(p);
    }
    */
    
    ///////////////////////////////////////////////////
    
    public void fromTenant(Tenant t)
    {
        this.setHandle(t.getHandle());        
        this.setAliases(t.getAliases());
    }
    
    // ///////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Tenant other = (Tenant) obj;

        return com.google.common.base.Objects.equal(this.handle, other.handle);
    }

    @Override
    public int hashCode()
    {
        return com.google.common.base.Objects.hashCode(this.handle);
    }

    @Override
    public String toString()
    {
        return com.google.common.base.Objects.toStringHelper(this)
            .addValue(this.handle).toString();
    }

}
