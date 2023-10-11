package com.galibhaskar.URL_Shortner.services;

import com.galibhaskar.URL_Shortner.concerns.ICSVService;
import com.galibhaskar.URL_Shortner.concerns.IHelperService;
import com.galibhaskar.URL_Shortner.models.ShortURL;
import com.galibhaskar.URL_Shortner.concerns.IURLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class URLService implements IURLService, ApplicationRunner {
    final ICSVService csvService;

    final IHelperService helperService;

    List<ShortURL> data;

    @Value("${shortCodeLength:5}")
    private int SHORT_CODE_LENGTH;

    public URLService(@Autowired ICSVService csvService,
                      @Autowired IHelperService helperService) {
        this.csvService = csvService;
        this.helperService = helperService;
    }

    @Override
    public ShortURL getURLInfo(String shortURL) throws Exception {
        Optional<ShortURL> shortURLResult = this.data.stream()
                .filter(_url -> Objects.equals(_url.getShortCode(), shortURL))
                .findFirst();

        if (shortURLResult.isPresent())
            return shortURLResult.get();

        throw new Exception("URL not found");
    }

    @Override
    public String getLongURL(String shortURL) throws Exception {
        Date currentDate = new Date();

        Optional<ShortURL> result = this.data.stream()
                .filter(_url -> Objects.equals(_url.getShortCode(), shortURL))
                .findFirst();

        if (result.isEmpty())
            throw new RuntimeException("URL Not Found");

        String expiryDate = result.get().getExpiryDate();

        Date convertedExpiryDate = helperService.parseDateString(expiryDate);

        if (currentDate.after(convertedExpiryDate))
            throw new RuntimeException("URL Expired");

        return result.get().getLongURL();
    }

    @Override
    public String createShortURL(String longURL, String expiry, String customShortCode)
            throws Exception {

        String shortCode;
        if (customShortCode == null)
            shortCode = helperService.generateRandomString(SHORT_CODE_LENGTH);
        else
            shortCode = customShortCode;

        ShortURL shortURL = new ShortURL(longURL, shortCode, expiry);

        csvService.addItemToCSV(shortURL);

        this.data.add(shortURL);

        return shortCode;
    }

    @Override
    public boolean updateShortURL(String shortURL, String longURL) throws Exception {
        AtomicBoolean updateSuccess = new AtomicBoolean(false);

        this.data.forEach(_urlItem -> {
            if (Objects.equals(_urlItem.getShortCode(), shortURL)) {
                _urlItem.setLongURL(longURL);

                updateSuccess.set(true);
            }
        });

        csvService.writeToCSV(this.data);

        return updateSuccess.get();
    }

    @Override
    public boolean updateExpiry(String shortURL, int daysToAddToExpiryDate) throws Exception {
        Optional<ShortURL> result = this.data.stream()
                .filter(_url -> Objects.equals(_url.getShortCode(), shortURL))
                .findFirst();

        if (result.isEmpty())
            throw new Exception("URL not found");

        String expiryDate = result.get().getExpiryDate();

        String extendedDate = helperService.getExtendedDate(expiryDate, daysToAddToExpiryDate);

        this.data.forEach(_urlItem -> {
            if (Objects.equals(_urlItem.getShortCode(), shortURL)) {
                _urlItem.setExpiryDate(extendedDate);
            }
        });

        csvService.writeToCSV(this.data);

        return true;
    }

    @Override
    public void deleteExpiredURLs() throws Exception {
        this.data = this.data.stream().filter(_url -> {
            Date urlExpiry = null;
            try {
                urlExpiry = helperService.parseDateString(_url.getExpiryDate());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Date currentDate = new Date();
            return !currentDate.after(urlExpiry);
        }).toList();

        csvService.writeToCSV(this.data);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.data = csvService.readFromCSV();

        this.data.forEach(obj -> System.out.println(obj.getLongURL()));
    }
}