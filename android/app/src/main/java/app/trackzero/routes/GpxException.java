package app.trackzero.routes;

import java.io.IOException;

public final class GpxException extends IOException
{
  private static final long serialVersionUID = 1L;

  public GpxException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
