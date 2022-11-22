package name.abuchen.portfolio.ui.views;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.primitives.Doubles;

import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.util.chart.TimelineChart;
import name.abuchen.portfolio.ui.views.SecuritiesChart.ChartInterval;

public class DividendYield
{
    private Client client;
    private Security security;
    private List<SecurityPrice> prices;
    private LocalDate startDate;
    private ChartLineSeriesAxes DY;
    private List<LocalDate> datesDY;
    private List<Double> valuesDY;

    public DividendYield(Client client, Security security, ChartInterval interval)
    {
        this.client = client;
        this.security = security;
        this.startDate = interval.getStart();
        this.DY = new ChartLineSeriesAxes();
        this.datesDY = new ArrayList<>();
        this.valuesDY = new ArrayList<>();
        
        this.calculateDividenYieldActual();
    }

    /**
     * Returns the calculated Simple Moving Average
     * 
     * @return The ChartLineSeriesAxes contains the X and Y Axes of the
     *         generated SMA
     */
    public ChartLineSeriesAxes getDY()
    {
        return this.DY;
    }

    /**
     * Calculates the Dividend Yield (DY) for a year of dividends from the
     * given startDate on. The method returns an object containing the X and Y
     * Axes of the generated DY.
     */
    private void calculateDividenYieldActual()
    {
        if (security == null)
            return;

        this.prices = security.getPricesIncludingLatest().stream() //
                        .filter(p ->  startDate == null || startDate.isBefore(p.getDate()))
                        .collect(Collectors.toList());

        if (prices == null)
            return;

        
        List<AccountTransaction> paidDividends = 
        client.getAccounts().stream().flatMap(a -> a.getTransactions().stream()) //
                .filter(t -> t.getSecurity() == security)
                .filter(t -> t.getType() == AccountTransaction.Type.DIVIDENDS)
                .collect(Collectors.toList());

        if (paidDividends == null)
            return;

        //do calculation
        for (int index = 0; index < prices.size(); index++)
        {
            //LocalDate nextDate = prices.get(index).getTime();
            //LocalDate isBefore = nextDate.plusDays(1);
            //LocalDate isAfter = isBefore.minusDays( Period.ofYears(1).getDays() + 1L);
            
            final int tindex = index;
            List<Double> k = new ArrayList<Double>();
            paidDividends.stream()
                         .filter(t -> ( t.getDateTime().toLocalDate().isBefore(prices.get(tindex).getDate()) && 
                                        t.getDateTime().toLocalDate().isAfter(prices.get(tindex).getDate().minusYears(1))) )
                         .forEach(t -> {    
                                        Optional<Unit> grossValue = t.getUnit(Unit.Type.GROSS_VALUE);
                                        long gross = grossValue.isPresent() ? grossValue.get().getForex().getAmount()
                                                                            : t.getGrossValueAmount();
                                        long perShare = Math.round(gross * Values.Share.divider() * Values.Quote.factorToMoney());
                                        if (t.getShares() != 0L ) 
                                            perShare = perShare / t.getShares(); 
                                        
                                        k.add( perShare / Values.Quote.divider() ); 
                                       }
                                  );

            //go on
            double dividendAmountLastYear = 0;// <- place search function here. either real dividend transactions or dividend events.
            for (double value:k)
                dividendAmountLastYear += value;
            
            double dividendYield = ( dividendAmountLastYear * 100 ) /
                                   ( prices.get(index).getValue() / Values.Quote.divider() );

            valuesDY.add(dividendYield);
            datesDY.add(prices.get(index).getDate());
        }
        LocalDate[] tmpDates = datesDY.toArray(new LocalDate[0]);

        this.DY.setDates(TimelineChart.toJavaUtilDate(tmpDates));
        this.DY.setValues(Doubles.toArray(valuesDY));
    }
    
    /**
     * Calculates the Dividend Yield (DY) for a year of dividends from the
     * given startDate on. The method returns an object containing the X and Y
     * Axes of the generated DY.
     */
    private void calculateDividenYieldInvestment()
    {
        if (security == null)
            return;
        
        List<AccountTransaction> securityMovement = 
        client.getAccounts().stream().flatMap(a -> a.getTransactions().stream())
                .filter(t -> t.getSecurity() == security) //
                .filter(t -> t.getType() == AccountTransaction.Type.BUY)
                //.filter(t -> t.getType() == AccountTransaction.Type.SELL)
                //.filter(t -> t.getType() == AccountTransaction.Type.TRANSFER_IN)
                //.filter(t -> t.getType() == AccountTransaction.Type.TRANSFER_OUT)
                //.filter(t -> t.getType() == AccountTransaction.Type.DEPOSIT)
                //.filter(t -> t.getType() == AccountTransaction.Type.REMOVAL)
                .collect(Collectors.toList());
        Collections.sort( securityMovement, new Comparator<AccountTransaction>() {
                                                        @Override
                                                        public int compare(AccountTransaction first, AccountTransaction second) 
                                                        {
                                                            return first.getDateTime().toLocalDate().compareTo( second.getDateTime().toLocalDate() );
                                                        }
                                                        });

        this.prices = security.getPricesIncludingLatest().stream() //
                        .filter(p ->  startDate == null || startDate.isBefore(p.getDate()))
                        .collect(Collectors.toList());

        if (prices == null)
            return;

        
        List<AccountTransaction> paidDividends = 
        client.getAccounts().stream().flatMap(a -> a.getTransactions().stream()) //
                .filter(t -> t.getSecurity() == security)
                .filter(t -> t.getType() == AccountTransaction.Type.DIVIDENDS)
                .collect(Collectors.toList());
        
        if (paidDividends == null)
            return;
        
        AccountTransaction sM = securityMovement.get(0);
        LocalDate sMDate;
        double amount = 0;
        long shareCount = 0;
        if ( sM.getType() == AccountTransaction.Type.BUY || 
             sM.getType() == AccountTransaction.Type.TRANSFER_IN ||
             sM.getType() == AccountTransaction.Type.DEPOSIT )
        {
            sMDate = sM.getDateTime().toLocalDate();
            amount = sM.getAmount();
            shareCount = sM.getShares();
        }
        //do calculation
        for (int index = 0; index < prices.size(); index++)
        {
            
            final int tindex = index;
            List<Double> k = new ArrayList<Double>();
            paidDividends.stream()
                         .filter(t -> ( t.getDateTime().toLocalDate().isBefore(prices.get(tindex).getDate()) && 
                                        t.getDateTime().toLocalDate().isAfter(prices.get(tindex).getDate().minusYears(1))) )
                         .forEach(t -> {    
                                        Optional<Unit> grossValue = t.getUnit(Unit.Type.GROSS_VALUE);
                                        long gross = grossValue.isPresent() ? grossValue.get().getForex().getAmount()
                                                                            : t.getGrossValueAmount();
                                        double perShare = Math.round(gross * Values.Share.divider() * Values.Quote.factorToMoney());
                                        if (t.getShares() != 0L ) 
                                            perShare = perShare / t.getShares(); 
                                        
                                        k.add( perShare / Values.Quote.divider() ); 
                                       }
                                  );

            //go on
            double dividendAmountLastYear = 0;// <- place search function here. either real dividend transactions or dividend events.
            for (double value:k)
                dividendAmountLastYear += value;
            
            double dividendYield = ( dividendAmountLastYear * 100 ) /
                                   ( prices.get(index).getValue() / Values.Quote.divider() );

            valuesDY.add(dividendYield);
            datesDY.add(prices.get(index).getDate());
        }
        LocalDate[] tmpDates = datesDY.toArray(new LocalDate[0]);

        this.DY.setDates(TimelineChart.toJavaUtilDate(tmpDates));
        this.DY.setValues(Doubles.toArray(valuesDY));
    }
}
