package crosstab;

public abstract class AbstractCrosstabAttributes implements AbstractCrosstabAttributesConstants, Serializable {
    @UI(displayName = "Horizontal Page", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private Integer hpage;

    public Integer getHpage() {
        return hpage;
    }

    public void setHpage(Integer hpage) {
        this.hpage = hpage;
    }

    public String getHpageAttributeName() {
        return AbstractCrosstabAttributes.HPAGE_PROPERTY;
    }

    abstract public List<String> getAttributesForCrosstab();
    abstract public List<String> getLabelAttributesForCrosstab();
}
