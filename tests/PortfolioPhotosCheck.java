package com.linart.tvpreview;

import java.util.List;

public final class PortfolioPhotosCheck {
    public static void main(String[] args) {
        String work = "<section class=\"project-covers\">\n"
                + "<a class=\"project-cover js-project-cover-touch hold-space\" href=\"/street\" data-context=\"pages\">"
                + "<img class=\"cover__img js-lazy\" src=\"https://cdn.myportfolio.com/a/b_rwc_0x0x1000x750x32.jpg?h=60c2\"></a>\n"
                + "<a class=\"project-cover js-project-cover-touch hold-space\" href=\"/hasidim\">x</a>\n"
                + "<a href=\"/street\" class=\"project-cover\">dup</a><a href=\"/contact\">c</a></section>";
        List<String> galleries = PortfolioPhotos.jurekGalleries(work);
        check(galleries.size() == 2 && galleries.get(0).equals("/street") && galleries.get(1).equals("/hasidim"));

        String gallery = "<title>Janusz Jurek - HASIDIM - reportage</title>"
                + "<img src=\"https://cdn.myportfolio.com/f0/3c_rw_1920.jpg?h=d94e\" srcset=\"https://cdn.myportfolio.com/f0/3c_rw_600.jpg?h=9ff0 600w,"
                + "https://cdn.myportfolio.com/f0/3c_rw_1200.jpg?h=502f 1200w,https://cdn.myportfolio.com/f0/3c_rw_1920.jpg?h=d94e 1920w,\">"
                + "<img data-src=\"https://cdn.myportfolio.com/f0/77_rw_1200.jpg?h=aa11\" data-srcset=\"https://cdn.myportfolio.com/f0/77_rw_600.jpg?h=bb22 600w,"
                + "https://cdn.myportfolio.com/f0/77_rw_1200.jpg?h=aa11 1200w\">"
                + "<img src=\"https://cdn.myportfolio.com/f0/99_rw_3840.jpg?h=cc33\"><img src=\"https://cdn.myportfolio.com/f0/cv_rwc_0x0x1x1x500.jpg?h=dd44\">";
        check(PortfolioPhotos.jurekGalleryTitle(gallery).equals("Hasidim"));
        check(PortfolioPhotos.jurekGalleryTitle("<title>Janusz Jurek - KAWAŁEK ZIEMI - projekt</title>").equals("Kawałek ziemi"));
        check(PortfolioPhotos.jurekGalleryTitle("<title>Janusz Jurek - STREET PHOTO project</title>").equals("Street photo"));
        List<String> images = PortfolioPhotos.jurekImages(gallery);
        check(images.size() == 2);
        check(images.get(0).equals("https://cdn.myportfolio.com/f0/3c_rw_1920.jpg?h=d94e"));
        check(images.get(1).equals("https://cdn.myportfolio.com/f0/77_rw_1200.jpg?h=aa11"));

        String wordpress = "<link rel=\"icon\" href=\"https://www.marcinurbanowicz.pl/wp-content/uploads/2024/08/cropped-ds-32x32.jpg\">"
                + "<a href=\"https://www.marcinurbanowicz.pl/wp-content/uploads/2023/11/marcin-urbanowicz-reportaz-slubny-2023_1.jpg\">"
                + "<img src=\"https://www.marcinurbanowicz.pl/wp-content/uploads/2023/11/marcin-urbanowicz-reportaz-slubny-2023_1-300x200.jpg\"></a>"
                + "<a href=\"https://www.marcinurbanowicz.pl/wp-content/uploads/2023/11/marcin-urbanowicz-reportaz-slubny-2023_1.jpg\">dup</a>"
                + "<a href=\"https://www.marcinurbanowicz.pl/wp-content/uploads/2018/01/reportaz-nowe2024_11.jpg\">b</a>"
                + "<a href=\"https://www.marcinurbanowicz.pl/wp-content/uploads/2018/01/x-1024x683.jpg\">resized</a>";
        List<String> wp = PortfolioPhotos.urbanowiczImages(wordpress);
        check(wp.size() == 2 && wp.get(0).endsWith("2023_1.jpg") && wp.get(1).endsWith("nowe2024_11.jpg"));
        System.out.println("Portfolio photo checks: 9 passed");
    }

    private static void check(boolean valid) {
        if (!valid) throw new AssertionError("Portfolio page parsing changed");
    }
}
