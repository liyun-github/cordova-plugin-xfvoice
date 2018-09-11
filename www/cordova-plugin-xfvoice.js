var exec = require('cordova/exec');

exports.startVoice = function (arg0, success, error) {
    exec(success, error, 'Voice', 'startVoice', [arg0]);
};
