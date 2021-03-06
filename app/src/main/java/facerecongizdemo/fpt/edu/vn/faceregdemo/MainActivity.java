package facerecongizdemo.fpt.edu.vn.faceregdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import android.app.*;
import android.content.*;
import android.net.*;
import android.view.*;
import android.graphics.*;
import android.widget.*;
import android.provider.*;
import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler; //imgView zoom in out lib
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;



public class MainActivity extends AppCompatActivity {

    private final int PICK_IMAGE = 1;
    private final int CAPTURE_IMG=2;
    private Face[] facesDetected;
    private String groupID="team1";
    Bitmap mBitmapp;
    ImageMatrixTouchHandler touchZoomHandler;
    double relation; //Xac dinh text size
    Button browserBtn, cameraBtn, identifyBtn;
    private Uri imageUri;
    private FaceServiceClient faceServiceClient =
            new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0", "a1df5290c9c14a54b1c723a471953709");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView imgView = findViewById(R.id.imageView1);
        touchZoomHandler = new ImageMatrixTouchHandler(imgView.getContext());
        imgView.setOnTouchListener(touchZoomHandler);
        browserBtn =findViewById(R.id.button1);
        browserBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
                gallIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(gallIntent, "Select Picture"), PICK_IMAGE);
            }
        });
        identifyBtn =findViewById(R.id.button);
        identifyBtn.setEnabled(false);
        identifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //detect all face
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                mBitmapp.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
                byte[] imageInByte = outputStream.toByteArray();
                long lengthbmp = imageInByte.length; //image file size in bytes
                if(lengthbmp<=4194304) {
                    //File <= 4MB accepted
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
                    new detectTask().execute(inputStream);
                    //Identify all detected faces
                }
                else{
                    //Compress file size to fit 4MB
                    long compressPercent = 4194304/(lengthbmp/100);
                    ByteArrayOutputStream compressOut = new ByteArrayOutputStream();
                    mBitmapp.compress(Bitmap.CompressFormat.JPEG,(int)compressPercent,compressOut);
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(compressOut.toByteArray());
                    new detectTask().execute(inputStream);
                    //Identify all detected faces
                }

            }
        });
        cameraBtn= findViewById(R.id.button2);
        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                File photo = new File(Environment.getExternalStorageDirectory(),"TempIMG");
                intent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photo));
                imageUri = Uri.fromFile(photo);
                startActivityForResult(intent, CAPTURE_IMG);

            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                mBitmapp = bitmap;
                ImageView imageView = findViewById(R.id.imageView1);
                imageView.setImageBitmap(bitmap);
                identifyBtn.setEnabled(true);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (requestCode == CAPTURE_IMG && resultCode == RESULT_OK) {
            try {

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                mBitmapp = bitmap;
                ImageView imageView = findViewById(R.id.imageView1);
                imageView.setImageBitmap(bitmap);
                identifyBtn.setEnabled(true);

            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT)
                        .show();

            }

        }
    }

  //Xac dinh khuon mac
    class detectTask extends  AsyncTask<InputStream,String,Face[]> {
        private ProgressDialog mDialog = new ProgressDialog(MainActivity.this);


        @Override
        protected Face[] doInBackground(InputStream... params) {
            try{
                publishProgress("Detecting...");
                Face[] results = faceServiceClient.detect(params[0],true,false,null);
                if(results == null)
                {
                    publishProgress("Detection Finished. Nothing detected");
                    return null;
                }
                else{
                    publishProgress(String.format("Detection Finished. %d face(s) detected",results.length));
                    return results;
                }
            }
            catch (Exception ex)
            {
                publishProgress("Detection failed");
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected void onPostExecute(Face[] faces) {
            mDialog.dismiss();
           if(faces!=null){
               facesDetected = faces;
               final UUID[] faceIds = new UUID[facesDetected.length];
               for(int i=0;i<facesDetected.length;i++){
                   faceIds[i] = facesDetected[i].faceId;
               }
               new IdentificationTask(groupID).execute(chunkArray(faceIds,10));
           }
            else{
               Toast.makeText(getApplicationContext(),"No face detected or Check your internet connection",Toast.LENGTH_LONG).show();

           }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            mDialog.setMessage(values[0]);
        }
    }


    //Task Nhan dang
    private class IdentificationTask extends AsyncTask<UUID[],String,IdentifyResult[]> {
        String personGroupId;

        private ProgressDialog mDialog = new ProgressDialog(MainActivity.this);

        public IdentificationTask(String personGroupId) {
            this.personGroupId = personGroupId;
        }

        @Override
        protected IdentifyResult[] doInBackground(UUID[]... params) {
            try{
                publishProgress("Getting person group status...");
                TrainingStatus trainingStatus  = faceServiceClient.getPersonGroupTrainingStatus(this.personGroupId);
                if(trainingStatus.status != TrainingStatus.Status.Succeeded)
                {
                    publishProgress("Person group training status is "+trainingStatus.status);
                    return null;
                }
                publishProgress("Identifying...");
                IdentifyResult[][] results = new IdentifyResult[params.length][10];
                for(int i =0;i<params.length;i++){
                        results[i] = faceServiceClient.identity(personGroupId, // person group id
                                params[i] // face ids
                                ,1); // max number of candidates returned
                }

                return flatten(results);

            } catch (Exception e)
            {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected void onPostExecute(IdentifyResult[] identifyResults) {
            mDialog.dismiss();
            for(int i=0;i<identifyResults.length;i++){
                if(identifyResults[i].candidates.size()!=0 && identifyResults[i].candidates.get(0).confidence>=0.65) {

                    new PersonDetectionTask(personGroupId,facesDetected[i]).execute(identifyResults[i].candidates.get(0).personId);
                }
                else  {
                    ImageView img = findViewById(R.id.imageView1);
                    img.setImageBitmap(drawFaceRectangleOnBitmap(mBitmapp,facesDetected[i],"Unknow"));

                }
            }
           identifyBtn.setEnabled(false);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            mDialog.setMessage(values[0]);
        }
    }

    //Get person và vẽ ô vuông
    private class PersonDetectionTask extends AsyncTask<UUID,String,Person> {
        private ProgressDialog mDialog = new ProgressDialog(MainActivity.this);
        private String personGroupId;
        private Face personFace;

        public PersonDetectionTask(String personGroupId, Face personFace) {

            this.personGroupId = personGroupId;
            this.personFace= personFace;
        }

        @Override
        protected Person doInBackground(UUID... params) {
            try{
                publishProgress("Getting person group status...");

                return faceServiceClient.getPerson(personGroupId,params[0]);
            } catch (Exception e)
            {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected void onPostExecute(Person person) {
            mDialog.dismiss();
            ImageView img =findViewById(R.id.imageView1);
            img.setImageBitmap(drawFaceRectangleOnBitmap(mBitmapp,this.personFace,person.name));

        }

        @Override
        protected void onProgressUpdate(String... values) {
            mDialog.setMessage(values[0]);
        }
    }

    //Ve hình chu nhat xac dinh mat

    private Bitmap drawFaceRectangleOnBitmap(Bitmap mBitmap, Face faceDetected,String name) {

        Bitmap bitmap = mBitmap.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas = new Canvas(bitmap);
        //Rectangle
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5);

        if(facesDetected != null)
        {
            FaceRectangle faceRectangle = faceDetected.faceRectangle;
            canvas.drawRect(faceRectangle.left,
                    faceRectangle.top,
                    faceRectangle.left+faceRectangle.width,
                    faceRectangle.top+faceRectangle.height,
                    paint);
            relation = Math.sqrt(canvas.getWidth() * canvas.getHeight())/250;
            double textSize = relation*3;
            drawTextOnCanvas(canvas,faceRectangle.left,faceRectangle.top+faceRectangle.height+(int)textSize,Color.RED,textSize,name);
        }
        mBitmapp=bitmap;
        return bitmap;
    }



    public static UUID[][] chunkArray(UUID[] array, int chunkSize) {
        int numOfChunks = (int)Math.ceil((double)array.length / chunkSize);
        UUID[][] output = new UUID[numOfChunks][];

        for(int i = 0; i < numOfChunks; ++i) {
            int start = i * chunkSize;
            int length = Math.min(array.length - start, chunkSize);

            UUID[] temp = new UUID[length];
            System.arraycopy(array, start, temp, 0, length);
            output[i] = temp;
        }

        return output;
    }

    public static IdentifyResult[] flatten(IdentifyResult[][] data) {
        ArrayList<IdentifyResult> list = new ArrayList<>();

        for(int i = 0; i < data.length; i++) {
            list.addAll( Arrays.asList(data[i]) );
        }

        return list.toArray(new IdentifyResult[0]);
    }


    //Viet text duoi hinh vuong
    private void drawTextOnCanvas(Canvas canvas, int x, int y, int color,double size, String name) {


        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize((float)size);
        paint.setTextAlign(Paint.Align.LEFT);

        canvas.drawText(name,x,y,paint);

    }

}
