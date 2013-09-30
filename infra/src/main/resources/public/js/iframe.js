var messenger = (function(){
	"use strict";

	var parentUrl;

	var send = function(message){
		if(parentUrl !== undefined){
			parent.postMessage(JSON.stringify(message), parentUrl);
		}
	};

	var requireResize = function(){
		var bodySize = $('body').outerHeight(true);

		var windowSize = 0;
		if($('.lightbox-window').length > 0){
			windowSize = $('.lightbox-window').outerHeight(true) + $('.lightbox-window').offset().top;
		}

		var newSize = bodySize;
		if(windowSize > bodySize){
			newSize = windowSize;
		}

		var appSizeMessage = {
			name: 'resize',
			data: {
				height: newSize + 1
			}
		};

		send(appSizeMessage);
	};

	var messagesHandlers = {
		'set-history': function(message){
			var history = message.data;
			for(var i = 0; i < message.data.length; i++){
				window.history.pushState({ link: message.data[i] }, null, '/?app=' + message.data[i]);
			}
		},
		'lightbox-position': function(message){
			var top = message.data.posY + (message.data.viewportHeight - $('.lightbox-window').height()) / 2;
			if(top < 0){
				top = 0;
			}

			$('.lightbox-window').offset({
				top: top
			});

			requireResize();
		},
		'set-style': function(message){
			if($('link[href="' + message.data + '"]').length > 0){
				return;
			}

			var updateView = function(){
				$('body').show();

				var appSizeMessage = {
					name: 'resize',
					data: {
						height: $('body').height() + 1
					}
				};

				send(appSizeMessage);
			};

			var nbStylesheets = document.styleSheets.length;
			$('<link />', {
					rel: 'stylesheet',
					href: message.data,
					type: 'text/css'
				})
				.appendTo('head')
				.attr('data-portal-style', message.data)
				.on('load', function(){
					updateView();
				});

			//we need to give back the main thread to the browser, so it can add the stylesheet to the document
			setTimeout(function(){
				if(document.styleSheets.length > nbStylesheets){
					//loading is done from cache, which means "load" event won't be called at all
					updateView();
				}
			}, 50);
		}
	};

	if(window.addEventListener){
		window.addEventListener('message', function(messageData){
			parentUrl = messageData.origin;
			var message = JSON.parse(messageData.data);
			messagesHandlers[message.name](message);
		});
	}

	return {
		sendMessage: function(message){
			send(message);
		},
		redirectParent: function(location){
			send({
				name: 'redirect-parent',
				data: location
			});
		},
		requireResize: requireResize,
		requireLightbox: function(){
			var appSizeMessage = {
				name: 'lightbox',
				data: {
				}
			};

			send(appSizeMessage);
		},
		notify: function(type, message){
			send({
				name: 'notify',
				data: {
					type: type,
					message: message
				}
			});
		},
		closeLightbox: function(){
			var appSizeMessage = {
				name: 'close-lightbox',
				data: {
				}
			};

			send(appSizeMessage);
		},
		updateAvatar: function(){
			messenger.sendMessage({
				name: 'update-avatar',
				data: {}
			})
		}
	};
}());

var navigationController = (function(){
	"use strict";

	One.filter('disconnected', function(event){
		messenger.redirectParent('/');
	})

	var app = Object.create(oneApp);
	app.scope = 'nav[role=apps-navigation]';
	app.start = function(){
		this.init();
	};

	app.define({
		action: {
			redirect: function(data){
				messenger.sendMessage({
					name: 'redirect',
					data: data.url
				});
			},
			moveHistory: function(data){
				messenger.sendMessage({
					name: 'move-history',
					data: data
				})
			}
		}
	});

	return app;
}());

$(document).ready(function(){
	"use strict";

	if(parent !== window && $('link[data-portal-style]').length === 0){
		$('body').hide();
	}

	navigationController.start();
});
