package app.trackzero.routes;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public final class GpxParser
{
  private GpxParser()
  {
  }

  public static GpxTrack parse(InputStream in) throws GpxException
  {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    feature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    feature(factory, "http://xml.org/sax/features/external-general-entities", false);
    feature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
    feature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    Handler handler = new Handler();
    try
    {
      factory.newSAXParser().parse(in, handler);
      return handler.result();
    }
    catch (SAXException | ParserConfigurationException | IOException e)
    {
      throw new GpxException("Unable to read GPX", e);
    }
  }

  private static void feature(SAXParserFactory factory, String name, boolean enabled)
  {
    try
    {
      factory.setFeature(name, enabled);
    }
    catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e)
    {
      // Android parsers may not support every feature; the entity resolver is a fallback.
    }
  }

  private static double number(String value)
  {
    try
    {
      double result = Double.parseDouble(value);
      return Double.isNaN(result) || Double.isInfinite(result) ? Double.NaN : result;
    }
    catch (NumberFormatException | NullPointerException e)
    {
      return Double.NaN;
    }
  }

  private static long time(String value)
  {
    try
    {
      return OffsetDateTime.parse(value).toInstant().toEpochMilli();
    }
    catch (DateTimeException | ArithmeticException e)
    {
      try
      {
        return Instant.parse(value).toEpochMilli();
      }
      catch (DateTimeException | ArithmeticException ignored)
      {
        return 0;
      }
    }
  }

  private static final class Point
  {
    final double lat, lon;
    double ele = Double.NaN;
    long timeMs;
    String name = "";

    Point(Attributes attributes)
    {
      lat = number(attributes.getValue("lat"));
      lon = number(attributes.getValue("lon"));
    }

    boolean valid()
    {
      return Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }
  }

  private static final class Handler extends DefaultHandler
  {
    final List<Point> tracks = new ArrayList<>();
    final List<Point> routes = new ArrayList<>();
    final List<GpxTrack.Waypoint> waypoints = new ArrayList<>();
    final StringBuilder text = new StringBuilder();
    String path = "";
    String capturePath;
    String metadataName, trackName, routeName;
    int trackCount, routeCount;
    Point point;

    @Override
    public InputSource resolveEntity(String publicId, String systemId)
    {
      return new InputSource(new StringReader(""));
    }

    @Override
    public void error(SAXParseException e) throws SAXException
    {
      throw e;
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException
    {
      throw e;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException
    {
      if (path.isEmpty() && !"gpx".equals(localName))
      {
        throw new SAXException("Root element is not GPX");
      }
      path += "/" + localName;
      if (path.equals("/gpx/trk"))
      {
        trackCount++;
      }
      else if (path.equals("/gpx/rte"))
      {
        routeCount++;
      }
      else if (path.equals("/gpx/trk/trkseg/trkpt") || path.equals("/gpx/rte/rtept")
               || path.equals("/gpx/wpt"))
      {
        point = new Point(attributes);
      }
      if (path.equals("/gpx/metadata/name") || path.equals("/gpx/trk/name")
          || path.equals("/gpx/rte/name") || path.equals("/gpx/wpt/name")
          || path.equals("/gpx/trk/trkseg/trkpt/ele") || path.equals("/gpx/rte/rtept/ele")
          || path.equals("/gpx/trk/trkseg/trkpt/time") || path.equals("/gpx/rte/rtept/time"))
      {
        capturePath = path;
        text.setLength(0);
      }
    }

    @Override
    public void characters(char[] ch, int start, int length)
    {
      if (capturePath != null)
      {
        text.append(ch, start, length);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
    {
      if (path.equals(capturePath))
      {
        String value = text.toString().trim();
        if (localName.equals("ele") && point != null)
        {
          point.ele = number(value);
        }
        else if (localName.equals("time") && point != null)
        {
          point.timeMs = time(value);
        }
        else if (!value.isEmpty())
        {
          if (path.equals("/gpx/metadata/name") && metadataName == null)
          {
            metadataName = value;
          }
          else if (path.equals("/gpx/trk/name") && trackCount == 1 && trackName == null)
          {
            trackName = value;
          }
          else if (path.equals("/gpx/rte/name") && routeCount == 1 && routeName == null)
          {
            routeName = value;
          }
          else if (path.equals("/gpx/wpt/name") && point != null)
          {
            point.name = value;
          }
        }
        capturePath = null;
      }
      if (path.equals("/gpx/trk/trkseg/trkpt") || path.equals("/gpx/rte/rtept")
          || path.equals("/gpx/wpt"))
      {
        if (point.valid())
        {
          if (path.equals("/gpx/wpt"))
          {
            waypoints.add(new GpxTrack.Waypoint(point.lat, point.lon, point.name));
          }
          else
          {
            (path.equals("/gpx/rte/rtept") ? routes : tracks).add(point);
          }
        }
        point = null;
      }
      path = path.substring(0, path.lastIndexOf('/'));
    }

    GpxTrack result()
    {
      List<Point> points = tracks.isEmpty() ? routes : tracks;
      int n = points.size();
      double[] lat = new double[n], lon = new double[n], ele = new double[n];
      long[] timeMs = new long[n];
      for (int i = 0; i < n; i++)
      {
        Point p = points.get(i);
        lat[i] = p.lat;
        lon[i] = p.lon;
        ele[i] = p.ele;
        timeMs[i] = p.timeMs;
      }
      String name = metadataName != null ? metadataName : trackName != null ? trackName : routeName;
      return new GpxTrack(name, lat, lon, ele, timeMs, waypoints);
    }
  }
}
