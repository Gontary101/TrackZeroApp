package app.trackzero.routes;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    // Element kinds by position in the document. Anything not listed is OTHER, and so is everything under it.
    static final int NONE = 0, OTHER = 1, GPX = 2, METADATA = 3, TRK = 4, RTE = 5, TRKSEG = 6, TRKPT = 7,
                     RTEPT = 8, WPT = 9, METADATA_NAME = 10, TRK_NAME = 11, RTE_NAME = 12, WPT_NAME = 13, ELE = 14,
                     TIME = 15;

    final List<Point> tracks = new ArrayList<>();
    final List<Point> routes = new ArrayList<>();
    final List<GpxTrack.Waypoint> waypoints = new ArrayList<>();
    final StringBuilder text = new StringBuilder();
    int[] stack = new int[16];
    int depth;
    /** Depth of the element whose text is being collected, or -1. */
    int captureDepth = -1;
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

    static int kind(int parent, String name)
    {
      switch (parent)
      {
      case NONE: return "gpx".equals(name) ? GPX : NONE;
      case GPX:
        switch (name)
        {
        case "metadata": return METADATA;
        case "trk": return TRK;
        case "rte": return RTE;
        case "wpt": return WPT;
        default: return OTHER;
        }
      case METADATA: return "name".equals(name) ? METADATA_NAME : OTHER;
      case TRK: return "name".equals(name) ? TRK_NAME : "trkseg".equals(name) ? TRKSEG : OTHER;
      case TRKSEG: return "trkpt".equals(name) ? TRKPT : OTHER;
      case RTE: return "name".equals(name) ? RTE_NAME : "rtept".equals(name) ? RTEPT : OTHER;
      case WPT: return "name".equals(name) ? WPT_NAME : OTHER;
      case TRKPT:
      case RTEPT: return "ele".equals(name) ? ELE : "time".equals(name) ? TIME : OTHER;
      default: return OTHER;
      }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException
    {
      final int kind = kind(depth == 0 ? NONE : stack[depth - 1], localName);
      if (kind == NONE)
      {
        throw new SAXException("Root element is not GPX");
      }
      if (depth == stack.length)
      {
        stack = Arrays.copyOf(stack, depth * 2);
      }
      stack[depth++] = kind;
      switch (kind)
      {
      case TRK -> trackCount++;
      case RTE -> routeCount++;
      case TRKPT, RTEPT, WPT -> point = new Point(attributes);
      case METADATA_NAME, TRK_NAME, RTE_NAME, WPT_NAME, ELE, TIME ->
      {
        captureDepth = depth;
        text.setLength(0);
      }
      default -> {}
      }
    }

    @Override
    public void characters(char[] ch, int start, int length)
    {
      if (captureDepth >= 0)
      {
        text.append(ch, start, length);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
    {
      final int kind = stack[depth - 1];
      if (depth-- == captureDepth)
      {
        captureDepth = -1;
        final String value = text.toString().trim();
        switch (kind)
        {
        case ELE -> point.ele = number(value);
        case TIME -> point.timeMs = time(value);
        case METADATA_NAME ->
        {
          if (metadataName == null && !value.isEmpty())
            metadataName = value;
        }
        case TRK_NAME ->
        {
          if (trackCount == 1 && trackName == null && !value.isEmpty())
            trackName = value;
        }
        case RTE_NAME ->
        {
          if (routeCount == 1 && routeName == null && !value.isEmpty())
            routeName = value;
        }
        case WPT_NAME -> point.name = value;
        default -> {}
        }
        return;
      }
      if (kind == TRKPT || kind == RTEPT || kind == WPT)
      {
        if (point.valid())
        {
          if (kind == WPT)
          {
            waypoints.add(new GpxTrack.Waypoint(point.lat, point.lon, point.name));
          }
          else
          {
            (kind == RTEPT ? routes : tracks).add(point);
          }
        }
        point = null;
      }
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
