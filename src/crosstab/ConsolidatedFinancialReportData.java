package crosstab;

public class ConsolidatedFinancialReportData extends FinancialReportData implements ConsolidatedFinancialReportDataConstants, Crosstabable {

    @UI(displayName = "Crosstab Columns", componentType = UIComponentType.BOUND_TEXT_FIELD)
    private SixColumnCrosstabAttributes crosstabBean = new SixColumnCrosstabAttributes();

    public ConsolidatedFinancialReportData() {
        super();
    }

    public ConsolidatedFinancialReportData(AccountBalanceH balance, AccountRollupMap map, int detailRollupLevel, int summaryRollupLevel, Map<Stakeholder, Stakeholder> buMap, Boolean decomposeRbu) {
        super(balance, map, detailRollupLevel, summaryRollupLevel, buMap, decomposeRbu);
    }

    public void setCrosstab(SixColumnCrosstabAttributes crosstab) {
        this.crosstabBean = crosstab;
    }

    public AbstractCrosstabAttributes getCrosstabBean() {
        return crosstabBean;
    }

    public String getCrosstabBeanProperty() {
        return ConsolidatedFinancialReportData.CROSSTAB_BEAN_PROPERTY;
    }

}