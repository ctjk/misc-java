package jooqFetch;

public class FetchPriceSettlementsJooqCommand extends JooqCommand {
    private List<TradingCalendar> calendarList;
    private Date asOfDate;
    private DateRange businessDateRange;
    private Long promptMonthStart;
    private Long numPromptMonths;
    private DateRange calendarPeriod;
    private DateRange tradingPeriod;
    private Price price;
    private List<PriceSettlementReportData> priceSettlements = new ArrayList<>();
    private PriceH prompt = PRICE_H.as("prompt");

    public FetchPriceSettlementsJooqCommand(List<TradingCalendar> calendarList, Date asOfDate, DateRange businessDateRange, Long promptMonthStart, Long numPromptMonths) {
        this.calendarList = calendarList;
        if (asOfDate != null) {
            this.asOfDate = asOfDate;
        } else {
            this.asOfDate = new Date();
        }
        this.businessDateRange = businessDateRange;
        if (promptMonthStart != null) {
            this.promptMonthStart = promptMonthStart;
        } else {
            this.promptMonthStart = 0L;
        }
        if (numPromptMonths != null) {
            this.numPromptMonths = numPromptMonths;
        } else {
            this.numPromptMonths = 1L;
        }
    }

    public void setCalendarPeriod(DateRange calendarPeriod) {
        this.calendarPeriod = calendarPeriod;
    }

    public void setTradingPeriod(DateRange tradingPeriod) {
        this.tradingPeriod = tradingPeriod;
    }

    public void setPrice(Price price) {
        this.price = price;
    }

    @Override
    public void doWork(DSLContext sql) {
        // If there is at least one calendar tagged as a non-future, then we need to execute the spot/balmo logic for the cash price.
        // Otherwise, we can just run the main prompt query.
        // If no calendars were specified we have to assume that at least one will be tagged as a non-future.
        boolean needCashLogic = true;
        if (calendarList != null && !calendarList.isEmpty()) {
            boolean onlyFutures = true;
            for (TradingCalendar calendar : calendarList) {
                if (!calendar.isFutureFlg()) {
                    onlyFutures = false;
                    break;
                }
            }
            if (onlyFutures) {
                needCashLogic = false;
            }
        }


        // For pricing data, note the as_of_date is equivalent to business date
        SelectQuery<Record> promptQuery = sql.selectQuery();

        buildBaseSelect(promptQuery);
        promptQuery.addConditions(PRICE_CATEGORY.FUTURE_PRICE_FLG.eq("Y"));
        PriceD promptPdEffSub = PRICE_D.as("pdEffSub");
        PriceD promptPdAsOfSub = PRICE_D.as("pdAsOfSub");
        promptQuery.addJoin(PRICE_D, JoinType.LEFT_OUTER_JOIN,
                PRICE_H.PRICE_H_ID.eq(PRICE_D.PRICE_H_ID),
                PRICE_D.PRICE_D_TYPE.eq(PriceDetailType.FUTURE.getDbValue()),
                PRICE_D.INACTIVE_DATE.isNull(),
                DSL.trunc(PRICE_D.EFFECTIVE_DATE, DatePart.MONTH).le(V_MONTHS.MONTH),
                PRICE_D.EFFECTIVE_DATE.eq(DSL.select(promptPdEffSub.EFFECTIVE_DATE.max())
                                                 .from(promptPdEffSub)
                                                 .where(promptPdEffSub.PRICE_H_ID.eq(PRICE_H.PRICE_H_ID))
                                                 .and(promptPdEffSub.INACTIVE_DATE.isNull())
                                                 .and(promptPdEffSub.PRICE_D_TYPE.eq(PriceDetailType.FUTURE.getDbValue()))
                                                 .and(DSL.trunc(promptPdEffSub.EFFECTIVE_DATE, DatePart.MONTH).le(V_MONTHS.MONTH))
                                                 .and(promptPdEffSub.AS_OF_DATE.le(DateUtil.getDateAsTimestamp(asOfDate)))
                                                 .and(promptPdEffSub.AS_OF_DATE.le(CALENDAR_PERIOD.BUSINESS_DATE))),
                PRICE_D.AS_OF_DATE.eq(DSL.select(promptPdAsOfSub.AS_OF_DATE.max())
                                                 .from(promptPdAsOfSub)
                                                 .where(promptPdAsOfSub.PRICE_H_ID.eq(PRICE_H.PRICE_H_ID))
                                                 .and(promptPdAsOfSub.INACTIVE_DATE.isNull())
                                                 .and(promptPdAsOfSub.PRICE_D_TYPE.eq(PriceDetailType.FUTURE.getDbValue()))
                                                 .and(promptPdAsOfSub.EFFECTIVE_DATE.eq(PRICE_D.EFFECTIVE_DATE))
                                                 .and(promptPdAsOfSub.AS_OF_DATE.le(DateUtil.getDateAsTimestamp(asOfDate)))
                                                 .and(promptPdAsOfSub.AS_OF_DATE.le(CALENDAR_PERIOD.BUSINESS_DATE))));
        promptQuery.addConditions(PRICE_H.PRICE_H_ID.eq(prompt.PRICE_H_ID));
        // This constrains the prompt months to the last futures period for the calendar; there is no point looking beyond that as there is no price data.
        // Note: this is not strictly necessary, as the calendar_periods would already be limited by non-existence of the calendar_d records (we need the latter to create the former),
        // but is is causing fetch perf issues ATM by taking it out.
        promptQuery.addConditions(V_MONTHS.MONTH.le(DSL.select(CALENDAR_D.PERIOD_START_DATE.max())
                                                 .from(CALENDAR_D)
                                                 .where(CALENDAR_D.CALENDAR_H_ID.eq(CALENDAR_H.CALENDAR_H_ID))
                                                 .and(CALENDAR_D.CALENDAR_EVENT_TYPE.eq("FUTURES_EXPIRY"))));

        if (needCashLogic) {
            SelectQuery<Record> spotQuery = sql.selectQuery();
            buildBaseSelect(spotQuery);
            spotQuery.addConditions(CALENDAR_H.FUTURE_FLG.eq("N"));
            spotQuery.addConditions(PRICE_CATEGORY.FUTURE_PRICE_FLG.eq("N"));
            spotQuery.addConditions(V_MONTHS.MONTH.eq(DSL.trunc(CALENDAR_PERIOD.BUSINESS_DATE, DatePart.MONTH)));
            spotQuery.addConditions(CALENDAR_PERIOD.BUSINESS_DATE.le(DateUtil.getDateAsTimestamp(asOfDate)));
            PriceD spotPdEffSub = PRICE_D.as("pdEffSub");
            PriceD spotPdAsOfSub = PRICE_D.as("pdAsOfSub");
            spotQuery.addJoin(PRICE_D, JoinType.LEFT_OUTER_JOIN,
                    PRICE_H.PRICE_H_ID.eq(PRICE_D.PRICE_H_ID),
                    PRICE_D.PRICE_D_TYPE.eq(PriceDetailType.ACTUAL.getDbValue()),
                    PRICE_D.INACTIVE_DATE.isNull(),
                    PRICE_D.EFFECTIVE_DATE.le(CALENDAR_PERIOD.BUSINESS_DATE),
                    PRICE_D.EFFECTIVE_DATE.eq(DSL.select(spotPdEffSub.EFFECTIVE_DATE.max())
                                                     .from(spotPdEffSub)
                                                     .where(spotPdEffSub.PRICE_H_ID.eq(PRICE_H.PRICE_H_ID))
                                                     .and(spotPdEffSub.INACTIVE_DATE.isNull())
                                                     .and(spotPdEffSub.PRICE_D_TYPE.eq(PRICE_D.PRICE_D_TYPE))
                                                     .and(DSL.trunc(spotPdEffSub.EFFECTIVE_DATE, DatePart.MONTH).le(V_MONTHS.MONTH))
                                                     .and(spotPdEffSub.AS_OF_DATE.le(DateUtil.getDateAsTimestamp(asOfDate)))
                                                     .and(spotPdEffSub.EFFECTIVE_DATE.le(CALENDAR_PERIOD.BUSINESS_DATE))),
                    PRICE_D.AS_OF_DATE.eq(DSL.select(spotPdAsOfSub.AS_OF_DATE.max())
                                                     .from(spotPdAsOfSub)
                                                     .where(spotPdAsOfSub.PRICE_H_ID.eq(PRICE_H.PRICE_H_ID))
                                                     .and(spotPdAsOfSub.INACTIVE_DATE.isNull())
                                                     .and(spotPdAsOfSub.PRICE_D_TYPE.eq(PRICE_D.PRICE_D_TYPE))
                                                     .and(spotPdAsOfSub.AS_OF_DATE.le(DateUtil.getDateAsTimestamp(asOfDate)))
                                                     .and(spotPdAsOfSub.EFFECTIVE_DATE.eq(PRICE_D.EFFECTIVE_DATE))));
            // Pull the spot price from the formula line that references the future price curve
            spotQuery.addJoin(PRICE_FORMULA, prompt.PRICE_H_ID.eq(PRICE_FORMULA.REF_PRICE_H_ID_OTHER));
            spotQuery.addConditions(PRICE_FORMULA.REF_PRICE_H_ID.eq(PRICE_H.PRICE_H_ID));

            SelectQuery<Record> balmoQuery = sql.selectQuery();
            buildBaseSelect(balmoQuery);
            balmoQuery.addConditions(CALENDAR_H.FUTURE_FLG.eq("N"));
            balmoQuery.addConditions(PRICE_CATEGORY.FUTURE_PRICE_FLG.eq("N"));
            balmoQuery.addConditions(PRICE_CATEGORY.FORMULA_FLG.eq("Y"));
            balmoQuery.addConditions(CALENDAR_PERIOD.BUSINESS_DATE.gt(DateUtil.getDateAsTimestamp(asOfDate)));
            PriceD balmoPdSub = PRICE_D.as("pdSub");
            balmoQuery.addJoin(PRICE_D, JoinType.LEFT_OUTER_JOIN,
                    PRICE_H.PRICE_H_ID.eq(PRICE_D.PRICE_H_ID),
                    PRICE_D.PRICE_D_TYPE.eq(PriceDetailType.ACTUAL.getDbValue()),
                    PRICE_D.INACTIVE_DATE.isNull(),
                    V_MONTHS.MONTH.eq(DSL.trunc(PRICE_D.EFFECTIVE_DATE, DatePart.MONTH)),
                    PRICE_D.AS_OF_DATE.eq(DSL.select(balmoPdSub.AS_OF_DATE.max())
                                                     .from(balmoPdSub)
                                                     .where(balmoPdSub.PRICE_H_ID.eq(PRICE_H.PRICE_H_ID))
                                                     .and(balmoPdSub.INACTIVE_DATE.isNull())
                                                     .and(DSL.trunc(balmoPdSub.EFFECTIVE_DATE, DatePart.MONTH).eq(V_MONTHS.MONTH))
                                                     .and(balmoPdSub.PRICE_D_TYPE.eq(PRICE_D.PRICE_D_TYPE))
                                                     .and(balmoPdSub.AS_OF_DATE.le(DateUtil.getDateAsTimestamp(asOfDate)))
                                                     .and(balmoPdSub.AS_OF_DATE.le(CALENDAR_PERIOD.BUSINESS_DATE))));
            // Pull the balmo price from the formula line that references the future price curve
            balmoQuery.addJoin(PRICE_FORMULA, prompt.PRICE_H_ID.eq(PRICE_FORMULA.REF_PRICE_H_ID_OTHER));
            balmoQuery.addConditions(PRICE_FORMULA.PRICE_H_ID.eq(PRICE_H.PRICE_H_ID));

            promptQuery.union(spotQuery);
            promptQuery.union(balmoQuery);
        }

        Result<Record> records = promptQuery.fetch();

        Map<Long, Price> prices = new HashMap<>();
        records.forEach((Record record) -> {
            Price price =  prices.computeIfAbsent( record.getValue(prompt.PRICE_H_ID).longValue(), t -> Commands.fetchByPrimaryKey(Price.class, record.getValue(prompt.PRICE_H_ID).longValue(),getContextSet()));

            PriceSettlementReportData priceSettlement = new PriceSettlementReportData();
            priceSettlement.setCalendarPeriodId(record.getValue(CALENDAR_PERIOD.CALENDAR_PERIOD_ID).longValue());
            priceSettlement.setCalendarId(record.getValue(CALENDAR_PERIOD.CALENDAR_H_ID).longValue());
            priceSettlement.setCalendarDescription(record.getValue(CALENDAR_H.DESCRIPTION));
            priceSettlement.setBusinessDate(record.getValue(CALENDAR_PERIOD.BUSINESS_DATE));
            priceSettlement.setFuturesPeriodStart(record.getValue(CALENDAR_PERIOD.FUTURES_PERIOD_START));
            priceSettlement.setFuturesPeriodEnd(record.getValue(CALENDAR_PERIOD.FUTURES_PERIOD_END));
            priceSettlement.setFuturesExpiry(record.getValue(CALENDAR_PERIOD.FUTURES_EXPIRY));
            priceSettlement.setOptionsPeriodStart(record.getValue(CALENDAR_PERIOD.OPTIONS_PERIOD_START));
            priceSettlement.setOptionsPeriodEnd(record.getValue(CALENDAR_PERIOD.OPTIONS_PERIOD_END));
            priceSettlement.setOptionsExpiry(record.getValue(CALENDAR_PERIOD.OPTIONS_EXPIRY));
            priceSettlement.setCashEffective(record.getValue(CALENDAR_PERIOD.CASH_EFFECTIVE));
            priceSettlement.setCalendarMonth(record.getValue(CALENDAR_PERIOD.CALENDAR_MONTH));
            priceSettlement.setBalanceMonth(record.getValue(CALENDAR_PERIOD.BALANCE_MONTH));
            priceSettlement.setPhysicalPrompt(record.getValue(CALENDAR_PERIOD.PHYSICAL_PROMPT));
            priceSettlement.setPrice(price);
            // If no pricing was found, the type will not be populated and we can just carry on
            if (record.getValue(PRICE_D.PRICE_D_TYPE) != null) {
                // Notes on cash price:
                // - the A ticks are always either Spot or Balance of Month ticks, by design, and can be slotted directly into that column
                // - for futures calendars, the future price becomes a proxy for the cash price - i.e. use the prompt+0 amount
                // - when the business day is after the as-of month, there are no spot / balmo prices - use the prompt+0 amount instead
                if (record.getValue(PRICE_D.PRICE_D_TYPE).equals(PriceDetailType.ACTUAL.getDbValue())) {
                    priceSettlement.setCashPrice(record.getValue(PRICE_D.PRICE_AMOUNT));
                } else {
                    priceSettlement.setPromptMonth(record.getValue(V_MONTHS.MONTH));
                    Integer p = DateUtil.getMonthsBetween(record.getValue(CALENDAR_PERIOD.FUTURES_PERIOD_START), record.getValue(V_MONTHS.MONTH)) - 1;
                    if (p == 0) {
                        priceSettlement.setPromptAmt(record.getValue(PRICE_D.PRICE_AMOUNT));
                        if (record.getValue(CALENDAR_H.FUTURE_FLG).equals("Y") || priceSettlement.getBusinessDate().after(DateUtil.getEndOfMonth(asOfDate))) {
                            priceSettlement.setCashPrice(record.getValue(PRICE_D.PRICE_AMOUNT));
                        }
                    } else if (p > 0 && p <= PriceSettlementReportData.MAX_PROMPT_MONTHS) {
                        try {
                            PropertyUtils.setProperty(priceSettlement, PriceSettlementReportData.PROMPT_AMT_PROPERTY + p.toString(), record.getValue(PRICE_D.PRICE_AMOUNT));
                        } catch (Exception e) {
                            throw new RuntimeException("Error setting Prompt Amt column", e);
                        }
                    }
                }
            }
            priceSettlements.add(priceSettlement);

        });

        // If there is no cash price, sub in the prompt price. This is intended primarily for cases where there is no balmo price for the month yet.
        for (PriceSettlementReportData priceSettlementReportData : priceSettlements) {
            if (priceSettlementReportData.getCashPrice() == null) {
                priceSettlementReportData.setCashPrice(priceSettlementReportData.getPromptAmt());
            }
        }
    }

    public List<PriceSettlementReportData> getPriceSettlements() {
        return priceSettlements;
    }

    private void buildBaseSelect(SelectQuery<Record> query) {
        PriceCategory promptCat = PRICE_CATEGORY.as("promptCat");
        query.addSelect(CALENDAR_PERIOD.CALENDAR_PERIOD_ID, CALENDAR_PERIOD.CALENDAR_H_ID, CALENDAR_H.DESCRIPTION, CALENDAR_PERIOD.BUSINESS_DATE, CALENDAR_PERIOD.FUTURES_PERIOD_START,
                        CALENDAR_PERIOD.FUTURES_PERIOD_END, CALENDAR_PERIOD.FUTURES_EXPIRY, CALENDAR_PERIOD.OPTIONS_PERIOD_START, CALENDAR_PERIOD.OPTIONS_PERIOD_END,
                        CALENDAR_PERIOD.OPTIONS_EXPIRY, CALENDAR_PERIOD.CASH_EFFECTIVE, CALENDAR_PERIOD.CALENDAR_MONTH, CALENDAR_PERIOD.BALANCE_MONTH, CALENDAR_PERIOD.PHYSICAL_PROMPT,
                        PRICE_H.PRICE_H_ID, V_MONTHS.MONTH, PRICE_D.PRICE_AMOUNT, PRICE_D.PRICE_D_TYPE, prompt.PRICE_H_ID, CALENDAR_H.FUTURE_FLG);
        query.addFrom(CALENDAR_PERIOD);
        query.addJoin(CALENDAR_H, CALENDAR_PERIOD.CALENDAR_H_ID.eq(CALENDAR_H.CALENDAR_H_ID));
        query.addJoin(PRICE_H, CALENDAR_H.CALENDAR_H_ID.eq(PRICE_H.CALENDAR_H_ID));
        query.addJoin(PRICE_CATEGORY, PRICE_H.PRICE_CATEGORY_ID.eq(PRICE_CATEGORY.PRICE_CATEGORY_ID));
        query.addJoin(prompt, CALENDAR_H.CALENDAR_H_ID.eq(prompt.CALENDAR_H_ID));
        query.addJoin(promptCat, prompt.PRICE_CATEGORY_ID.eq(promptCat.PRICE_CATEGORY_ID));
        query.addJoin(V_MONTHS, JoinType.CROSS_JOIN);

        // Apply additional filters from the contextSet
        if (!getContextSet().getExpiryDate().equals(DateUtil.SENTINEL_DATE)) {
            query.addConditions(Jooq.buildExpiryCondition(PRICE_H, getContextSet().getExpiryDate()));
            query.addConditions(Jooq.buildExpiryCondition(PRICE_CATEGORY, getContextSet().getExpiryDate()));
        }
        if(getContextSet().getContext(BusinessUnitContext.class) != null) {
            if (getContextSet().getContext(BusinessUnitContext.class).getBusinessUnitIds() != null && !getContextSet().getContext(BusinessUnitContext.class).getBusinessUnitIds().isEmpty()) {
                query.addConditions(Jooq.buildBusinessUnitFilterCondition(PRICE_H, getContextSet().getContext(BusinessUnitContext.class).getBusinessUnitIds()));
            }
        }

        if (calendarList != null && !calendarList.isEmpty()) {
            List<Long> ids = calendarList.stream().map(TradingCalendar::getCalendarId).collect(Collectors.toList());
            query.addConditions(prompt.CALENDAR_H_ID.in(ids));
        }

        // The extra join to the prompt price header is to ensure we always have the future price reference in the result set, for use later.
        // For the prompt version of this query it is completely redundant, but we need it for the spot and BalMo versions and it is a bit cleaner/simpler to add it to the common query
        query.addConditions(promptCat.FUTURE_PRICE_FLG.eq("Y"));
        if (price != null) {
            query.addConditions(prompt.PRICE_H_ID.eq(BigDecimal.valueOf(price.getPriceId())));
        }

        // Prompt Month 0 is the same as the futures period for the calendar / business date, do not look at any months prior to this
        query.addConditions(V_MONTHS.MONTH.ge(CALENDAR_PERIOD.FUTURES_PERIOD_START));
        // The prompt start month parameter may further constrain the prompt months
        query.addConditions(V_MONTHS.MONTH.ge(DSL.timestampAdd(CALENDAR_PERIOD.FUTURES_PERIOD_START, promptMonthStart, DatePart.MONTH)));
        // Limit prompt months per user provided value
        if (numPromptMonths != null) {
            //add_months to date
            query.addConditions(V_MONTHS.MONTH.le( DSL.timestampAdd(CALENDAR_PERIOD.FUTURES_PERIOD_START, (promptMonthStart + numPromptMonths - 1), DatePart.MONTH)));
        }

        // Apply other common filters as needed
        if (businessDateRange != null) {
            query.addConditions(CALENDAR_PERIOD.BUSINESS_DATE.between(DateUtil.getDateAsTimestamp(businessDateRange.getEffective()), DateUtil.getDateAsTimestamp(businessDateRange.getInactive())));
        }
        if (calendarPeriod != null) {
            query.addConditions(CALENDAR_PERIOD.CALENDAR_MONTH.between(DateUtil.getDateAsTimestamp(calendarPeriod.getEffective()), DateUtil.getDateAsTimestamp(calendarPeriod.getInactive())));
        }
        if (tradingPeriod != null) {
            query.addConditions(CALENDAR_PERIOD.FUTURES_PERIOD_START.between(DateUtil.getDateAsTimestamp(tradingPeriod.getEffective()), DateUtil.getDateAsTimestamp(tradingPeriod.getInactive())));
        }
    }

}
