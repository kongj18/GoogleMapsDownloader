package me.oque;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.*;

public class GoogleMap {

    MapTile start, end;
    String destinationFolder;
    private int height, width;
    ArrayList<String> images;

    public GoogleMap(MapTile start, MapTile end, String dest) {
        this.start = start;
        this.end = end;
        if (start.x > end.x || start.y > end.y)
            throw new IllegalArgumentException();
        destinationFolder = dest;
        height = end.y - start.y + 1;
        width = end.x - start.x + 1;
    }

    public GoogleMap(double startLat, double startLon, double endLat,
                     double endLon, int zoom, String dest) throws IllegalArgumentException {
        if (startLat < endLat || startLon > endLon)
            throw new IllegalArgumentException();
        start = new MapTile(startLat, startLon, zoom);
        end = new MapTile(endLat, endLon, zoom);
        destinationFolder = dest;
        height = end.y - start.y + 1;
        width = end.x - start.x + 1;
    }

    private void saveImage(String imageUrl, String fileName) throws IOException {
        /**
         * @param imageUrl - string, url of image
         * @param fileName - string, name of file to save image to
         * saves image from provided url to a local file in temp/ folder
         */
        String tempFolder;
        if ( System.getProperty("os.name").toLowerCase().contains("win") )
            tempFolder = "\\temp\\";
        else
            tempFolder = "/temp/";
        File dir = new File(destinationFolder + tempFolder);
        if (!dir.exists()) {
            dir.mkdir();
        }

        ExecutorService executor;
        Future<InputStream> future;
        boolean downloaded = false;
        while (!downloaded) {
            executor = Executors.newSingleThreadExecutor();
            future = executor.submit(new TimeoutURL(imageUrl));
            InputStream is;
            try {
                is = (future.get(3, TimeUnit.SECONDS));
                executor.shutdownNow();
                OutputStream os = new FileOutputStream(destinationFolder + tempFolder + fileName);

                byte[] b = new byte[2048];
                int length;

                while ((length = is.read(b)) != -1) {
                    os.write(b, 0, length);
                }

                is.close();
                os.close();
                downloaded = true;
            } catch (TimeoutException e) {
                Proxy.setProxy();
            } catch (Exception e) {
            } finally {
                executor.shutdownNow();
            }
        }
    }

    public void bulkDownload(boolean proxyEnabled)
            throws IOException, InterruptedException {
        /**
         * @param proxyEnabled - boolean, uses proxy to download maps if set to true
         * downloads images of tiles in a given area from west to east from north to south
         * uses proxy to bypass banning of IP
         */

        if (proxyEnabled)
            Proxy.setProxy();

        int latCounter = 0;
        int lonCounter = 0;
        images = new ArrayList<String>();

        MapTile current;

        long total = this.getNumTiles();
        int c = 1;

        System.out.println("Downloading images...");

        for (int i = start.x; i <= end.x; i++) {
            for (int j = start.y; j <= end.y; j++) {
                current = new MapTile(i, j, start.z);
                System.out.println("Downloading image " + c + " out of " + total);
                while (true) {
                    try {
                        saveImage(current.tileURL(), ("image_" + lonCounter + "_" + latCounter + ".jpg"));
                        images.add("image_" + lonCounter + "_" + latCounter + ".jpg");
                        break;
                    } catch (Exception e) {
                        if (proxyEnabled)
                            Proxy.setProxy();
                    }
                }
                lonCounter++;
                c++;
                if (proxyEnabled)
                    Thread.sleep(500);
                else
                    Thread.sleep(1000); // delay. Google bans ip very quickly
            }
            lonCounter = 0;
            latCounter++;
        }

        System.out.println("Download succeeded");
    }

    public void mergeImages() throws IOException {
        if (images == null)
            return;
        BufferedImage result = new BufferedImage(width*256, height*256, BufferedImage.TYPE_INT_RGB);
        Graphics g = result.getGraphics();
        int x = 0;
        int y = 0;

        System.out.println("Merging tiles into one image....");

        String tempFolder;
        if ( System.getProperty("os.name").toLowerCase().contains("win") )
            tempFolder = "\\temp\\";
        else
            tempFolder = "/temp/";

        for (String image : images) {
            File current = new File(destinationFolder + tempFolder + image);
            BufferedImage bi = ImageIO.read(current);
            g.drawImage(bi, x, y, null);
            y += 256;
            if (y >= result.getHeight()){
                y = 0;
                x += bi.getWidth();
            }
            current.delete();
        }

        File dir = new File(destinationFolder + tempFolder);

        if(dir.list().length == 0)
            dir.delete();

        ImageIO.write(result, "png", new File(destinationFolder + "/result.png"));

        String slash = System.getProperty("os.name").toLowerCase().contains("win") ? "\\" : "/";
        System.out.println("Done. Merged image can be found at " + destinationFolder + slash + "result.png");
    }

    public long getNumTiles() {
        return Math.abs( (end.y - start.y + 1) * (end.x - start.x + 1) );
    }

    public int getWidth() {
        return width*256;
    }

    public int getHeight() {
        return height*256;
    }

}