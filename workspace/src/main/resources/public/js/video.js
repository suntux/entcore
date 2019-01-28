const mediaSource = new MediaSource();
mediaSource.addEventListener('sourceopen', handleSourceOpen, false);
let mediaRecorder;
let recordedBlobs;
let sourceBuffer;
let ws;
let id;

const errorMsgElement = document.querySelector('span#errorMsg');
const recordedVideo = document.querySelector('video#recorded');
const recordButton = document.querySelector('button#record');
recordButton.addEventListener('click', () => {
  if (recordButton.textContent === 'Start Recording') {
    startRecording();
  } else {
    stopRecording();
    recordButton.textContent = 'Start Recording';
    playButton.disabled = false;
    downloadButton.disabled = false;
  }
});

const playButton = document.querySelector('button#play');
playButton.addEventListener('click', () => {
  const superBuffer = new Blob(recordedBlobs, {type: 'video/webm'});
  recordedVideo.src = null;
  recordedVideo.srcObject = null;
  recordedVideo.src = window.URL.createObjectURL(superBuffer);
  recordedVideo.controls = true;
});

const downloadButton = document.querySelector('button#download');
downloadButton.addEventListener('click', () => {
  const blob = new Blob(recordedBlobs, {type: 'video/webm'});
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.style.display = 'none';
  a.href = url;
  a.download = 'test.webm';
  document.body.appendChild(a);
  a.click();
  setTimeout(() => {
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }, 100);
});

function handleSourceOpen(event) {
  console.log('MediaSource opened');
  sourceBuffer = mediaSource.addSourceBuffer('video/webm; codecs="vp8"');
  console.log('Source buffer: ', sourceBuffer);
}

function handleDataAvailable(event) {
  if (event.data && event.data.size > 0) {
    recordedBlobs.push(event.data);
    ws.send(event.data);
    // var elchunk = recordedBlobs[recordedBlobs.length - 1];
    // var buffer = new ArrayBuffer(elchunk.length * 2);
    // var view = new DataView(buffer);
    // var index = 0;
    // for(var i = 0; i < elchunk.length; i++){
    //   view.setInt16(index, elchunk[i], true);
    //   index += 2;
    // }
    // console.log(buffer);
    // ws.send((new Uint8Array(buffer)));
  }
}

function startRecording() {
  recordedBlobs = [];
  let options = {mimeType: 'video/webm;codecs=vp9'};
  if (!MediaRecorder.isTypeSupported(options.mimeType)) {
    console.error(`${options.mimeType} is not Supported`);
    errorMsgElement.innerHTML = `${options.mimeType} is not Supported`;
    options = {mimeType: 'video/webm;codecs=vp8'};
    if (!MediaRecorder.isTypeSupported(options.mimeType)) {
      console.error(`${options.mimeType} is not Supported`);
      errorMsgElement.innerHTML = `${options.mimeType} is not Supported`;
      options = {mimeType: 'video/webm'};
      if (!MediaRecorder.isTypeSupported(options.mimeType)) {
        console.error(`${options.mimeType} is not Supported`);
        errorMsgElement.innerHTML = `${options.mimeType} is not Supported`;
        options = {mimeType: ''};
      }
    }
  }

  try {
    mediaRecorder = new MediaRecorder(window.stream, options);
  } catch (e) {
    console.error('Exception while creating MediaRecorder:', e);
    errorMsgElement.innerHTML = `Exception while creating MediaRecorder: ${JSON.stringify(e)}`;
    return;
  }

  console.log('Created MediaRecorder', mediaRecorder, 'with options', options);
  recordButton.textContent = 'Stop Recording';
  playButton.disabled = true;
  downloadButton.disabled = true;
  mediaRecorder.onstop = (event) => {
    console.log('Recorder stopped: ', event);
  };
  mediaRecorder.ondataavailable = handleDataAvailable;
  mediaRecorder.start(1000); // collect 1000ms of data
  console.log('MediaRecorder started', mediaRecorder);
}

function upload() {
  var formData = new FormData();
  formData.append("fname", id);
  formData.append("data", new Blob(recordedBlobs, {type: 'video/webm'}), "test-" + id + ".webm");
  var request = new XMLHttpRequest();
  request.open("POST", "/workspace/videoupload");
  request.send(formData);
}

function stopRecording() {
  mediaRecorder.stop();
  console.log('Recorded Blobs: ', recordedBlobs);
  ws.send("save-" + "test" + id);// this.title);
  upload();
}

function handleSuccess(stream) {
  recordButton.disabled = false;
  console.log('getUserMedia() got stream:', stream);
  window.stream = stream;

  const gumVideo = document.querySelector('video#gum');
  gumVideo.srcObject = stream;
}

function uuid() {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
		var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
		return v.toString(16);
	});
}

function initWS() {
	id = uuid();
	ws = new WebSocket((window.location.protocol === "https:" ? "wss": "ws") + "://" +
			"localhost:6800/video/" + id);
			//window.location.host + "/video/" + id);
	ws.onopen = function () {
//		if(player.currentTime > 0){
//			player.currentTime = 0;
//		}

//		that.status = 'recording';
//		notifyFollowers(this.status);
//		if(!loaded){
//			http().get('/infra/public/js/zlib.min.js').done(function(){
//				that.loadComponents();
//			}.bind(this));
//		}
	};
	ws.onerror = function (event) {
//		console.log(event);
//		that.status = 'stop';
//		notifyFollowers(that.status);
//		closeWs();
//		notify.error(event.data);
	}
	ws.onmessage = function (event) {
//		if (event.data && event.data.indexOf("error") !== -1) {
//			console.log(event.data);
//			closeWs();
//			notify.error(event.data);
//		} else if (event.data && event.data === "ok") {
//			closeWs();
//			notify.info("recorder.saved");
//		}

	}
	ws.onclose = function (event) {
//		that.status = 'stop';
//		clearWs();
	}
}

async function init(constraints) {
  try {
    const stream = await navigator.mediaDevices.getUserMedia(constraints);
    handleSuccess(stream);
  } catch (e) {
    console.error('navigator.getUserMedia error:', e);
    errorMsgElement.innerHTML = `navigator.getUserMedia error:${e.toString()}`;
  }
}

document.querySelector('button#start').addEventListener('click', async () => {
  initWS();
  const constraints = {
    audio: {
      echoCancellation: {exact: false}
    },
    video: {
      width: 1280, height: 720
    }
  };
  console.log('Using media constraints:', constraints);
  await init(constraints);
});
