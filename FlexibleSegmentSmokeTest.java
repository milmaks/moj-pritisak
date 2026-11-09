import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import rs.local.mojpritisak.FlexibleSevenSegmentReader;
public class FlexibleSegmentSmokeTest {
  public static void main(String[] a) throws Exception {
    String[] names={"sys","dia","pulse"};int[] expected={129,73,63};
    for(int row=0;row<3;row++){
      BufferedImage image=ImageIO.read(new File(a[row]));
      int w=image.getWidth(),h=image.getHeight();int[] p=image.getRGB(0,0,w,h,null,0,w);
      FlexibleSevenSegmentReader.Reading output=FlexibleSevenSegmentReader.read(w,h,p,row);
      System.out.println(names[row]+"="+(output==null?"unrecognized":output.value+" / "+output.confidence));
      if(output==null||output.value!=expected[row])System.exit(1);
    }
  }
}
