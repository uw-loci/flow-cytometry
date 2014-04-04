%Requesting new images
clc;
clear; 

%register prairie link com object
pl = actxserver('PrairieLink.ImageObject');
pl.registerevent({'ImageUpdated' 'NewPVImageHandler'});

for i=1:100
    pl.RequestImage()
    pause(0.01)
end




% pl = actxserver('PrairieLink.ImageObject');
% pl.registerevent(('ImageUpdate' 'NewPYImageHandler'))
% requestImages
% 
% save myself in matlab, not through prairie