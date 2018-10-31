package crosstab;

public class CrosstabReportHelper<T extends AbstractReportData> {
    private String hpageAttribute;
    private List<String> dataProviderAttributes;
    private List<String> labelAttributes;
    private String crosstabBeanProperty;
    private Map<Object, PageAndColumn> crosstabMap = new HashMap<Object, PageAndColumn>();
    private Map<Integer, List<String>> labelMap = new HashMap<Integer, List<String>>();
    private Integer maxPage;
    private Boolean showTotalColumn;
    private String totalColumn;

    public class PageAndColumn {
        private Integer hpage;
        private String dataProviderAttribute;

        public PageAndColumn(Integer hpage, String dataProviderAttribute) {
            this.hpage = hpage;
            this.dataProviderAttribute = dataProviderAttribute;
        }

        public Integer getHpage() {
            return hpage;
        }

        public String getDataProviderAttribute() {
            return dataProviderAttribute;
        }
    }


    public CrosstabReportHelper(Crosstabable crosstabReportData, Boolean showTotalColumn) {
        this.hpageAttribute = crosstabReportData.getCrosstabBean().getHpageAttributeName();
        this.dataProviderAttributes = crosstabReportData.getCrosstabBean().getAttributesForCrosstab();
        this.labelAttributes = crosstabReportData.getCrosstabBean().getLabelAttributesForCrosstab();
        this.crosstabBeanProperty = crosstabReportData.getCrosstabBeanProperty();
        this.showTotalColumn = showTotalColumn == null ? true : showTotalColumn;
    }

    public void buildCrosstabMap(List<Object> values, List<String> labels) {
        Integer page = 1;
        Iterator valueIterator = values.iterator();
        Iterator labelIterator = labels.iterator();
        boolean createTotal = values.size() > 1 && showTotalColumn;
        while (valueIterator.hasNext() || createTotal) {
            List<String> labelsForPage = new ArrayList<String>();
            for (String attribute : dataProviderAttributes) {
                if (valueIterator.hasNext()) {
                    Object value = valueIterator.next();
                    crosstabMap.put(value, new PageAndColumn(page, attribute));
                    String label = (String)labelIterator.next();
                    labelsForPage.add(label);
                    /* Map null values to the Undefined column as well - if not already mapped */
                    if (label.equals("Undefined") && value != null) {
                        crosstabMap.put(null, new PageAndColumn(page, attribute));
                    }
                } else {
                    if (createTotal) {
                        totalColumn = attribute;
                        labelsForPage.add("Total");
                        createTotal = false;
                        break;
                    }
                }
            }
            labelMap.put(page, labelsForPage);
            page ++;
        }
        maxPage = page - 1;
    }

    public List<Object> getDistinctObjectsFromResultSet(DetachedCriteria detachedCriteria, String attribute, Session session) {
        detachedCriteria.setProjection(Projections.distinct(Property.forName(attribute)));
        Criteria criteria = detachedCriteria.getExecutableCriteria(session);
        return criteria.list();
    }

    /*
        Note: sourceObjectForCrosstab could be the HDO object from the report fetch or perhaps the report data object itself. Left this as two separate parameters for
        flexibility; in some cases we might need to do some transformation/processing of the raw data on the HDO - in which case you could simply set an attribute on the
        report data object and pass it in.
    */
    public List<T> transpose(Object sourceObjectForCrosstab, String crosstabDataAttribute, T reportData, Object value) {
        List<T> results = new ArrayList<T>();

        try {
            /* Transpose the data for the provided source object - i.e. put the calculated object value in the appropriate hpage and crosstab column */
            Object crosstabValue = PropertyUtils.getProperty(sourceObjectForCrosstab, crosstabDataAttribute);
            PageAndColumn pageAndColumn = crosstabMap.get(crosstabValue);

            Object crosstabBean = PropertyUtils.getProperty(reportData, crosstabBeanProperty);
            Integer hPage = pageAndColumn.getHpage();
            PropertyUtils.setProperty(crosstabBean, hpageAttribute, hPage);

            intializePageToZeroes(hPage, crosstabBean);
            PropertyUtils.setProperty(crosstabBean, pageAndColumn.getDataProviderAttribute(), value);

            setLabelsForPage(hPage, crosstabBean);

            /* If this is the last page, then add the value to the Total column as well */
            if (hPage.compareTo(maxPage) == 0 && totalColumn != null) {
                PropertyUtils.setProperty(crosstabBean, totalColumn, value);
            }
            results.add(reportData);

            /*
                To circumvent the issue of sparse data, ensure that each page has a placeholder for the non crosstab attributes. For example if the DP is carrying an account
                code column, then a particular value of the account code has to appear on each hpage to ensure the pages are homogenous.
                It's an implementation decision to deal with the issue here, vs. trying to sort it out after all report rows have been derived. Also elected to assume the worst
                case (that the data is not represented on any of the other pages) vs. incurring the performance hit of searching to find out otherwise.
            */
            for (Integer i = 1; i <= maxPage; i++) {
                if (i.compareTo(hPage) != 0) {
                    //Clone the report data object for each page, setting the crosstab columns to zero and setting the labels for the page
                    T newReportData = cloneReportData(reportData);
                    crosstabBean = PropertyUtils.getProperty(newReportData, crosstabBeanProperty);
                    PropertyUtils.setProperty(crosstabBean, hpageAttribute, i);
                    intializePageToZeroes(i, crosstabBean);
                    setLabelsForPage(i, crosstabBean);
                    results.add(newReportData);
                }
            }

            /* If this is not the last page, create another report data object in order to add the value to the Total column */
            if (hPage.compareTo(maxPage) != 0 && totalColumn != null) {
                T newReportData = cloneReportData(reportData);
                crosstabBean = PropertyUtils.getProperty(newReportData, crosstabBeanProperty);
                PropertyUtils.setProperty(crosstabBean, hpageAttribute, maxPage);
                intializePageToZeroes(maxPage, crosstabBean);
                setLabelsForPage(maxPage, crosstabBean);
                PropertyUtils.setProperty(crosstabBean, totalColumn, value);
                results.add(newReportData);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return results;
    }

    private void intializePageToZeroes(Integer hPage, Object crosstabBean) throws Exception {
        Integer columnsUsed = labelMap.get(hPage).size();
        for (Integer i=0; i < dataProviderAttributes.size(); i++) {
            String columnToSet = dataProviderAttributes.get(i);
            if (i < columnsUsed) {
                PropertyUtils.setProperty(crosstabBean, columnToSet, BigDecimal.valueOf(0));
            } else {
                PropertyUtils.setProperty(crosstabBean, columnToSet, null);
            }
        }
    }

    private void setLabelsForPage(Integer hPage, Object crosstabBean) throws Exception {
        List<String> labels = labelMap.get(hPage);
        Integer columnsUsed = labels.size();
        for (Integer i=0; i < labelAttributes.size(); i++) {
            String columnToSet = labelAttributes.get(i);
            if (i < columnsUsed) {
                PropertyUtils.setProperty(crosstabBean, columnToSet, labels.get(i));
            } else {
                PropertyUtils.setProperty(crosstabBean, columnToSet, null);
            }
        }
    }

    private T cloneReportData(T sourceObject) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(sourceObject);
        out.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bis);
        return (T)in.readObject();
    }

}
