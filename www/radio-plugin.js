/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

var argscheck = require('cordova/argscheck'),
channel = require('cordova/channel'),
utils = require('cordova/utils'),
exec = require('cordova/exec'),
cordova = require('cordova');

//channel.createSticky('onCordovaInfoReady');
//Tell cordova channel to wait on the CordovaInfoReady event
//channel.waitForInitialization('onCordovaInfoReady');

/**
 *
 */
function RadioStream() {
	this.available = false;
	this.result = null;

	var me = this;

	channel.onCordovaReady.subscribe(function() {
		console.log("RadioStream cordova ready");
	});
}

/**
 * Get Radio info
 *
 * @param {Function} successCallback The function to call when the heading data is available
 * @param {Function} errorCallback The function to call when there is an error getting the heading data. (OPTIONAL)
 */
RadioStream.prototype.getInfo = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "RadioStream", "getInfo", []);
};

RadioStream.prototype.initialize = function(args, successCallback, errorCallback) {
	options = {
			defaultArtist: "Monkey Radio",
			streamingURL: "http://cdn.instream.audio:9189/stream",
			centovacastUser: "monkeyra",
			centovacastPass: "vlW13jF6u6",
			centovacastURL: "http://cdn.instream.audio:2199",
			lastfmApiKey: "812117bad533a130518319f6d6614edf",
			autoStart: true
	};

	if( args.autoStart != undefined ) options.autoStart = args.autoStart;
	if( args.streamingURL != undefined ) options.streamingURL = args.streamingURL;
	if( args.centovacastUser != undefined ) options.centovacastUser = args.centovacastUser;
	if( args.centovacastPass != undefined ) options.centovacastPass = args.centovacastPass;
	if( args.centovacastURL != undefined ) options.centovacastURL = args.centovacastURL;
	if( args.lastfmApiKey != undefined ) options.lastfmApiKey = args.lastfmApiKey;
	if( args.defaultArtist != undefined ) options.defaultArtist = args.defaultArtist;

	jsonArgs = JSON.stringify(options);
	exec(successCallback, errorCallback, "RadioStream", "initialize", [jsonArgs]);
};

RadioStream.prototype.play = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "RadioStream", "play", []);
};

RadioStream.prototype.stop = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "RadioStream", "stop", []);
};

RadioStream.prototype.getPlayerStatus = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "RadioStream", "getPlayerStatus", []);
};

RadioStream.prototype.getTrackInfo = function(successCallback, errorCallback) {
	exec(successCallback, errorCallback, "RadioStream", "getTrackInfo", []);
};

RadioStream.install = function () {
	if (!window.plugins) {
		window.plugins = {};
	}

	window.plugins.radiostream = new RadioStream();
	return window.plugins.radiostream;
};

module.exports = new RadioStream();

cordova.addConstructor(RadioStream.install);
