package crosstab;

public class SixColumnCrosstabAttributes extends AbstractCrosstabAttributes implements SixColumnCrosstabAttributesConstants{
    @UI(displayName = "Column 1", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private BigDecimal column1;
    @UI(displayName = "Column 2", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private BigDecimal column2;
    @UI(displayName = "Column 3", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private BigDecimal column3;
    @UI(displayName = "Column 4", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private BigDecimal column4;
    @UI(displayName = "Column 5", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private BigDecimal column5;
    @UI(displayName = "Column 6", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private BigDecimal column6;
    @UI(displayName = "Column 1 Label", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private String column1Label;
    @UI(displayName = "Column 2 Label", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private String column2Label;
    @UI(displayName = "Column 3 Label", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private String column3Label;
    @UI(displayName = "Column 4 Label", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private String column4Label;
    @UI(displayName = "Column 5 Label", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private String column5Label;
    @UI(displayName = "Column 6 Label", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private String column6Label;

    public BigDecimal getColumn1() {
        return column1;
    }

    public void setColumn1(BigDecimal column1) {
        this.column1 = column1;
    }

    public BigDecimal getColumn2() {
        return column2;
    }

    public void setColumn2(BigDecimal column2) {
        this.column2 = column2;
    }

    public BigDecimal getColumn3() {
        return column3;
    }

    public void setColumn3(BigDecimal column3) {
        this.column3 = column3;
    }

    public BigDecimal getColumn4() {
        return column4;
    }

    public void setColumn4(BigDecimal column4) {
        this.column4 = column4;
    }

    public BigDecimal getColumn5() {
        return column5;
    }

    public void setColumn5(BigDecimal column5) {
        this.column5 = column5;
    }

    public BigDecimal getColumn6() {
        return column6;
    }

    public void setColumn6(BigDecimal column6) {
        this.column6 = column6;
    }

    public String getColumn1Label() {
        return column1Label;
    }

    public void setColumn1Label(String column1Label) {
        this.column1Label = column1Label;
    }

    public String getColumn2Label() {
        return column2Label;
    }

    public void setColumn2Label(String column2Label) {
        this.column2Label = column2Label;
    }

    public String getColumn3Label() {
        return column3Label;
    }

    public void setColumn3Label(String column3Label) {
        this.column3Label = column3Label;
    }

    public String getColumn4Label() {
        return column4Label;
    }

    public void setColumn4Label(String column4Label) {
        this.column4Label = column4Label;
    }

    public String getColumn5Label() {
        return column5Label;
    }

    public void setColumn5Label(String column5Label) {
        this.column5Label = column5Label;
    }

    public String getColumn6Label() {
        return column6Label;
    }

    public void setColumn6Label(String column6Label) {
        this.column6Label = column6Label;
    }

    public List<String> getAttributesForCrosstab() {
        return Arrays.asList(SixColumnCrosstabAttributes.COLUMN1_PROPERTY, SixColumnCrosstabAttributes.COLUMN2_PROPERTY, SixColumnCrosstabAttributes.COLUMN3_PROPERTY, SixColumnCrosstabAttributes.COLUMN4_PROPERTY, SixColumnCrosstabAttributes.COLUMN5_PROPERTY, SixColumnCrosstabAttributes.COLUMN6_PROPERTY);
    }

    public List<String> getLabelAttributesForCrosstab() {
        return Arrays.asList(SixColumnCrosstabAttributes.COLUMN1_LABEL_PROPERTY, SixColumnCrosstabAttributes.COLUMN2_LABEL_PROPERTY, SixColumnCrosstabAttributes.COLUMN3_LABEL_PROPERTY, SixColumnCrosstabAttributes.COLUMN4_LABEL_PROPERTY, SixColumnCrosstabAttributes.COLUMN5_LABEL_PROPERTY, SixColumnCrosstabAttributes.COLUMN6_LABEL_PROPERTY);
    }
}